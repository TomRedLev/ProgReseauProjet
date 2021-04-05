package fr.upem.net.tcp.nonblocking;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import fr.upem.net.tcp.nonblocking.Reader.ProcessStatus;

public class ServerChat {

    static private class Context {

        final private SelectionKey key;
        final private SocketChannel sc;
        final private ByteBuffer bbin = ByteBuffer.allocate(BUFFER_SIZE);
        final private ByteBuffer bbout = ByteBuffer.allocate(BUFFER_SIZE);
        final private Queue<Message> queue = new LinkedList<>();
        final private ServerChat server;
        
        private final Charset UTF8_CHARSET = Charset.forName("UTF-8");
        private final MessageReader messageReader = new MessageReader();
        final private ByteReader byteReader = new ByteReader();
        private boolean closed = false;

        private Context(ServerChat server, SelectionKey key){
            this.key = key;
            this.sc = (SocketChannel) key.channel();
            this.server = server;
        }

        /**
         * Process the content of bbin
         *
         * The convention is that bbin is in write-mode before the call
         * to process and after the call
         *
         */
        private void processIn() {
        	for(;;){
        		Reader.ProcessStatus statusOP = byteReader.process(bbin);
         	   	if (statusOP == ProcessStatus.DONE) {
					var OPcode = byteReader.get();
					switch (OPcode) {
						case 0 :
							byteReader.reset();
							Reader.ProcessStatus status = messageReader.process(bbin);
							switch (status){
						    	case DONE:
						    		var msg = messageReader.get();
						    		System.out.println(msg.getLogin() + " : " + msg.getStr());
						    		server.broadcast(msg);
						    		messageReader.reset();
						    		break;
						    	case REFILL:
						    		return;
						    	case ERROR:
						    		silentlyClose();
						    		return;
							}
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
        	}
        	
        }

        /**
         * Add a message to the message queue, tries to fill bbOut and updateInterestOps
         *
         * @param msg
         */
        private void queueMessage(Message msg) {
        	queue.add(msg);
        	processOut();
        	updateInterestOps();
        }

        /**
         * Try to fill bbout from the message queue
         *
         */
        private void processOut() {
        	while (!queue.isEmpty()){
                var msg = queue.peek();
                var login = msg.getLogin();
                var str =  msg.getStr();
                var bbLog = UTF8_CHARSET.encode(login);
                var bb = UTF8_CHARSET.encode(str);
                if (bbLog.remaining() + bb.remaining() + 2 * Integer.BYTES <= bbout.remaining()){
                    queue.remove();
                    bbout.put((byte) 0);
                    bbout.putInt(login.length());
                    bbout.put(bbLog);
                    bbout.putInt(str.length());
                    bbout.put(bb);
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
        	int newInterestOps = 0;
			if (!closed && bbin.hasRemaining()) {
				newInterestOps = newInterestOps | SelectionKey.OP_READ;
			}
			if (bbout.position() != 0) {
				newInterestOps = newInterestOps | SelectionKey.OP_WRITE;
			}
			if (newInterestOps == 0){
				silentlyClose();
				return ;
			}
			key.interestOps(newInterestOps);
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
        	if (sc.read(bbin) == -1) {
        		closed = true;
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
        	updateInterestOps();
        }

    }

    static private int BUFFER_SIZE = 1_024;
    static private Logger logger = Logger.getLogger(ServerChat.class.getName());

    private final ServerSocketChannel serverSocketChannel;
    private final Selector selector;

    public ServerChat(int port) throws IOException {
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(port));
        selector = Selector.open();
    }

    public void launch() throws IOException {
		serverSocketChannel.configureBlocking(false);
		serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
		while(!Thread.interrupted()) {
			printKeys(); // for debug
			System.out.println("Starting select");
			try {
				selector.select(this::treatKey);
			} catch (UncheckedIOException tunneled) {
				throw tunneled.getCause();
			}
			System.out.println("Select finished");
		}
    }

	private void treatKey(SelectionKey key) {
		printSelectedKey(key); // for debug
		try {
			if (key.isValid() && key.isAcceptable()) {
				doAccept(key);
			}
		} catch(IOException ioe) {
			// lambda call in select requires to tunnel IOException
			throw new UncheckedIOException(ioe);
		}
		try {
			if (key.isValid() && key.isWritable()) {
				((Context) key.attachment()).doWrite();
			}
			if (key.isValid() && key.isReadable()) {
				((Context) key.attachment()).doRead();
			}
		} catch (IOException e) {
			logger.log(Level.INFO,"Connection closed with client due to IOException",e);
			silentlyClose(key);
		}
	}

    private void doAccept(SelectionKey key) throws IOException {
    	SocketChannel sc = serverSocketChannel.accept();
		if (sc == null) {
			logger.warning("The selector has lied");
			return ;
		}
		sc.configureBlocking(false);
		var clientKey = sc.register(selector, SelectionKey.OP_READ);
		clientKey.attach(new Context(this, clientKey));
    }

    private void silentlyClose(SelectionKey key) {
        Channel sc = (Channel) key.channel();
        try {
            sc.close();
        } catch (IOException e) {
            // ignore exception
        }
    }

    /**
     * Add a message to all connected clients queue
     *
     * @param msg
     */
    private void broadcast(Message msg) {
    	for (var key : selector.keys()) {
    		var ctxt = (Context) key.attachment();
    		if (ctxt != null) {
    			ctxt.queueMessage(msg);
    		}
    	}
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        if (args.length!=1){
            usage();
            return;
        }
        new ServerChat(Integer.parseInt(args[0])).launch();
    }

    private static void usage(){
        System.out.println("Usage : ServerSumBetter port");
    }

	/***
	 *  Theses methods are here to help understanding the behavior of the selector
	 ***/

	private String interestOpsToString(SelectionKey key){
		if (!key.isValid()) {
			return "CANCELLED";
		}
		int interestOps = key.interestOps();
		ArrayList<String> list = new ArrayList<>();
		if ((interestOps&SelectionKey.OP_ACCEPT)!=0) list.add("OP_ACCEPT");
		if ((interestOps&SelectionKey.OP_READ)!=0) list.add("OP_READ");
		if ((interestOps&SelectionKey.OP_WRITE)!=0) list.add("OP_WRITE");
		return String.join("|",list);
	}

	public void printKeys() {
		Set<SelectionKey> selectionKeySet = selector.keys();
		if (selectionKeySet.isEmpty()) {
			System.out.println("The selector contains no key : this should not happen!");
			return;
		}
		System.out.println("The selector contains:");
		for (SelectionKey key : selectionKeySet){
			SelectableChannel channel = key.channel();
			if (channel instanceof ServerSocketChannel) {
				System.out.println("\tKey for ServerSocketChannel : "+ interestOpsToString(key));
			} else {
				SocketChannel sc = (SocketChannel) channel;
				System.out.println("\tKey for Client "+ remoteAddressToString(sc) +" : "+ interestOpsToString(key));
			}
		}
	}

	private String remoteAddressToString(SocketChannel sc) {
		try {
			return sc.getRemoteAddress().toString();
		} catch (IOException e){
			return "???";
		}
	}

	public void printSelectedKey(SelectionKey key) {
		SelectableChannel channel = key.channel();
		if (channel instanceof ServerSocketChannel) {
			System.out.println("\tServerSocketChannel can perform : " + possibleActionsToString(key));
		} else {
			SocketChannel sc = (SocketChannel) channel;
			System.out.println("\tClient " + remoteAddressToString(sc) + " can perform : " + possibleActionsToString(key));
		}
	}

	private String possibleActionsToString(SelectionKey key) {
		if (!key.isValid()) {
			return "CANCELLED";
		}
		ArrayList<String> list = new ArrayList<>();
		if (key.isAcceptable()) list.add("ACCEPT");
		if (key.isReadable()) list.add("READ");
		if (key.isWritable()) list.add("WRITE");
		return String.join(" and ",list);
	}
}
