package fr.upem.net.tcp.nonblocking;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.logging.Logger;

import fr.upem.net.tcp.nonblocking.Reader.ProcessStatus;

public class ClientChat {

    static private class Context {

        final private SelectionKey key;
        final private SocketChannel sc;
        final private ClientChat client;
        final private ByteBuffer bbin = ByteBuffer.allocate(BUFFER_SIZE);
        final private ByteBuffer bbout = ByteBuffer.allocate(BUFFER_SIZE);
        final private Queue<ByteBuffer> queue = new LinkedList<>(); // buffers read-mode
        final private MessageReader messageReader = new MessageReader();
        final private ByteReader byteReader = new ByteReader();
        private boolean closed = false;
        
        private int connectID = 0;
        private boolean connected = false;
        private boolean waitingPacket = false;
        private byte OPcode = 127;

        private Context(ClientChat client, SelectionKey key){
            this.key = key;
            this.sc = (SocketChannel) key.channel();
            this.client = client;
        }

        /**
         * Process the content of bbin
         *
         * The convention is that bbin is in write-mode before the call
         * to process and after the call
         *
         */
        private void processIn() {
           for(;;) {
        	   Reader.ProcessStatus statusOP = null;
        	   if (OPcode == 127) {
        		   statusOP = byteReader.process(bbin);
       		   }
        	   if (OPcode != 127 || statusOP == ProcessStatus.DONE) {
        		   if (OPcode == 127) {
        	   			OPcode = byteReader.get();
        	   			byteReader.reset();
        	   	   } 
        		   switch (OPcode) {
        		   	   case 1 :
        		   		   System.out.println("You are connected as " + client.login + " !");
        		   		   connected = true;
        		   		   break;
        		   	   case 2 : 
        		   		   if (waitingPacket) {
        		   			   client.login = client.login.split("#")[0];
         		   		   	   connectID += 1;
         		   		   	   client.login = client.login + "#" + connectID;
         		   		   	   waitingPacket = false;
        		   		   }
        		   		   break;
        		   		   
	        		   case 3 :
	        			   byteReader.reset();
	        			   Reader.ProcessStatus status = messageReader.process(bbin);
	                	   if (status == ProcessStatus.DONE) {
	                		   Message msg =  messageReader.get();
	                		   System.out.println(msg.getLogin() + " : " + msg.getStr());
	                		   messageReader.reset();
	                	   }
	                	   else if (status == ProcessStatus.REFILL) {
	                		   return;
	                	   }
	                	   else if (status == ProcessStatus.ERROR) {
	                		   silentlyClose();
	                		   return ;
	                	   }
	                	   break;
	        		   case 4 :
	        			   byteReader.reset();
	        			   Reader.ProcessStatus statusPM = messageReader.process(bbin);
	                	   if (statusPM == ProcessStatus.DONE) {
	                		   Message msg =  messageReader.get();
	                		   System.out.println("PM from " + msg.getLogin() + " : " + msg.getStr());
	                		   messageReader.reset();
	                	   }
	                	   else if (statusPM == ProcessStatus.REFILL) {
	                		   return;
	                	   }
	                	   else if (statusPM == ProcessStatus.ERROR) {
	                		   silentlyClose();
	                		   return ;
	                	   }
	                	   break;
	        		   case 127 :
	        			   break;
	                   default : 
	                	   System.out.println("This OP code isn't allowed");
        		   }
        	   }
        	   else if (statusOP == ProcessStatus.REFILL) {
        		   return;
        	   }
        	   else if (statusOP == ProcessStatus.ERROR) {
        		   silentlyClose();
        		   return ;
        	   }
        	   
        	   OPcode = 127;
        	   
        	   
           }
        }

        /**
         * Add a message to the message queue, tries to fill bbOut and updateInterestOps
         *
         * @param bb
         */
        private void queueMessage(ByteBuffer bb) {
            queue.add(bb);
            processOut();
            updateInterestOps();
        }

        /**
         * Try to fill bbout from the message queue
         *
         */
        private void processOut() {
            while (!queue.isEmpty()){
                var bb = queue.peek();
                if (bb.remaining()<=bbout.remaining()){
                    queue.remove();
                    bbout.put(bb);
                } else {
                    break;
                }
            }
        }

        /**
         * Update the interestOps of the key looking
         * only at values of the boolean closed and
         * of both ByteBuffers.
         *
         * The convention is that both buffers are in write-mode before the call
         * to updateInterestOps and after the call.
         * Also it is assumed that process has been be called just
         * before updateInterestOps.
         */

        private void updateInterestOps() {
            var interesOps=0;
            if (!closed && bbin.hasRemaining()){
                interesOps=interesOps|SelectionKey.OP_READ;
            }
            if (bbout.position()!=0){
                interesOps|=SelectionKey.OP_WRITE;
            }
            if (interesOps==0){
                silentlyClose();
                return;
            }
            key.interestOps(interesOps);
        }

        private void silentlyClose() {
            try {
                sc.close();
            } catch (IOException e) {
                // ignore exception
            }
        }

        /**
         * Performs the read action on sc
         *
         * The convention is that both buffers are in write-mode before the call
         * to doRead and after the call
         *
         * @throws IOException
         */
        private void doRead() throws IOException {
            if (sc.read(bbin)==-1) {
                closed=true;
            }
            processIn();
            updateInterestOps();
        }

        /**
         * Performs the write action on sc
         *
         * The convention is that both buffers are in write-mode before the call
         * to doWrite and after the call
         *
         * @throws IOException
         */

        private void doWrite() throws IOException {
            bbout.flip();
            sc.write(bbout);
            bbout.compact();
            processOut();
            updateInterestOps();
        }

        public void doConnect() throws IOException {
            if (!sc.finishConnect())
            	return ;
            key.interestOps(SelectionKey.OP_READ);
        }
    }

    static private int BUFFER_SIZE = 10_000;
    static private Logger logger = Logger.getLogger(ClientChat.class.getName());

    private final Charset UTF8_CHARSET = Charset.forName("UTF-8");
    private final Object lock = new Object();
    
    private final SocketChannel sc;
    private final Selector selector;
    private final InetSocketAddress serverAddress;
    private String login;
    private final Thread console;
    private final ArrayBlockingQueue<String> commandQueue = new ArrayBlockingQueue<>(10);
    private Context uniqueContext;

    public ClientChat(String login, InetSocketAddress serverAddress) throws IOException {
        this.serverAddress = serverAddress;
        this.login = login;
        this.sc = SocketChannel.open();
        this.selector = Selector.open();
        this.console = new Thread(this::consoleRun);
    }

    private void consoleRun() {
        try (var scan = new Scanner(System.in)) { 
        	while (!uniqueContext.connected) {
        		processCommands();
        		selector.wakeup();
        	}
            while (scan.hasNextLine()) {
            	synchronized (lock) {
	                var msg = scan.nextLine();
	                sendCommand(msg);
            	}
            }
        } catch (InterruptedException e) {
            logger.info("Console thread has been interrupted");
        } finally {
            logger.info("Console thread stopping");
        }
    }

    /**
     * Send a command to the selector via commandQueue and wake it up
     *
     * @param msg
     * @throws InterruptedException
     */


    private void sendCommand(String msg) throws InterruptedException {
       commandQueue.add(msg);
       processCommands();
       selector.wakeup();
    }

    /**
     * Processes the command from commandQueue
     */

    private void processCommands(){
    	if (!uniqueContext.connected) {
    		var bbLog = UTF8_CHARSET.encode(login);
    		var bb = ByteBuffer.allocate(1 + bbLog.remaining() + Integer.BYTES);
    		bb.put((byte) 0);
    		bb.putInt(bbLog.remaining());
    		bb.put(bbLog);
    		bb.flip();
    		uniqueContext.waitingPacket = true;
    		uniqueContext.queueMessage(bb);
    	}
        while (!commandQueue.isEmpty()) {
        	var msg = commandQueue.remove();
        	var bbLog = UTF8_CHARSET.encode(login);
        	var bbStr = UTF8_CHARSET.encode(msg);
        	var bb = ByteBuffer.allocate(1 + bbLog.remaining() + bbStr.remaining() + Integer.BYTES * 2);
        	if (msg.startsWith("/")) {
        		var sb = new StringBuilder(msg);
        		sb.deleteCharAt(0);
        		msg = sb.toString();
        		var msgArray = msg.split(" ", 2);
        		bbStr = UTF8_CHARSET.encode(msgArray[1]);
        		var bbLogTarget = UTF8_CHARSET.encode(msgArray[0]);
        		bb = ByteBuffer.allocate(1 + bbLog.remaining() + bbLogTarget.remaining() + bbStr.remaining() + Integer.BYTES * 3);
        		bb.put((byte) 4);
        		bb.putInt(bbLog.remaining());
            	bb.put(bbLog);
            	bb.putInt(bbLogTarget.remaining());
            	bb.put(bbLogTarget);
        	} else {
        		bb.put((byte) 3);
        		bb.putInt(bbLog.remaining());
            	bb.put(bbLog);
        	}
        	
        	bb.putInt(bbStr.remaining());
        	bb.put(bbStr);
        	bb.flip();
        	uniqueContext.queueMessage(bb);
        }
    }

    public void launch() throws IOException {
        sc.configureBlocking(false);
        var key = sc.register(selector, SelectionKey.OP_CONNECT);
        uniqueContext = new Context(this, key);
        key.attach(uniqueContext);
        sc.connect(serverAddress);

        console.start();

        while(!Thread.interrupted()) {
            try {
                selector.select(this::treatKey);
                processCommands();
            } catch (UncheckedIOException tunneled) {
                throw tunneled.getCause();
            }
        }
    }

    private void treatKey(SelectionKey key) {
        try {
            if (key.isValid() && key.isConnectable()) {
                uniqueContext.doConnect();
            }
            if (key.isValid() && key.isWritable()) {
                uniqueContext.doWrite();
            }
            if (key.isValid() && key.isReadable()) {
                uniqueContext.doRead();
            }
        } catch(IOException ioe) {
            // lambda call in select requires to tunnel IOException
            throw new UncheckedIOException(ioe);
        }
    }


//    private void silentlyClose(SelectionKey key) {
//        Channel sc = (Channel) key.channel();
//        try {
//            sc.close();
//        } catch (IOException e) {
//            // ignore exception
//        }
//    }


    public static void main(String[] args) throws NumberFormatException, IOException {
        if (args.length!=3){
            usage();
            return;
        }
        new ClientChat(args[0],new InetSocketAddress(args[1],Integer.parseInt(args[2]))).launch();
    }

    private static void usage(){
        System.out.println("Usage : ClientChat login hostname port");
    }
}
