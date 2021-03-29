package fr.upem.net.tcp.nonblocking;

import fr.upem.net.tcp.nonblocking.IntReader;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class StringReader implements Reader<String> {
    private enum State {DONE, WAITING_FOR_SIZE, WAITING_FOR_CONTENT, ERROR};
    private final int MAX_SIZE = 1_024;
    private static final Charset UTF8 = StandardCharsets.UTF_8;
    private final IntReader intReader = new IntReader();
    private final ByteBuffer internalbb = ByteBuffer.allocate(MAX_SIZE);
    private State state = State.WAITING_FOR_SIZE;
    private int size;
    private String value;

    @Override
    public ProcessStatus process(ByteBuffer bb) {
    	for (;;) {
	        switch (state) {
	            case WAITING_FOR_SIZE:
	                var status = intReader.process(bb);
	                if (status != ProcessStatus.DONE) {
	        			return status;
	        		}
	        		size = intReader.get();
	        		if (size < 0 || size > 1024) {
	        			return ProcessStatus.ERROR;
	        		}
	        		internalbb.limit(size); 
	        		state = State.WAITING_FOR_CONTENT;
	        		break;
	        		
	            case WAITING_FOR_CONTENT:
	                // var missing = size - internalbb.position();
	                bb.flip();
	            	try {
	                    if (bb.remaining()<=internalbb.remaining()){
	                        internalbb.put(bb);
	                    } else {
	                        var oldLimit = bb.limit();
	                        bb.limit(internalbb.remaining());
	                        internalbb.put(bb);
	                        bb.limit(oldLimit);
	                    }
	                } finally {
	                    bb.compact();
	                }
	            	
	            	if (internalbb.hasRemaining()) {
	            		return ProcessStatus.REFILL;
	            	}
	            	
	            	state = State.DONE;
	            	internalbb.flip();
	            	value = UTF8.decode(internalbb).toString();
	            	return ProcessStatus.DONE;
	            default:
	                throw new IllegalStateException();
	        }
    	}

    }

    @Override
    public String get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return value;
    }

    @Override
    public void reset() {
        state = State.WAITING_FOR_SIZE;
        intReader.reset();
        internalbb.clear();
        value = null;
    }
}
