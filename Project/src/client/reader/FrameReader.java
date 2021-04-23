package fr.upem.net.tcp.nonblocking.reader;

import java.nio.ByteBuffer;

import fr.upem.net.tcp.nonblocking.frame.*;

public class FrameReader implements Reader<Frame> {
	private enum State {DONE,WAITING,ERROR};

    private State state = State.WAITING;
    private final ByteBuffer internalbb = ByteBuffer.allocate(Byte.BYTES); // write-mode
    private Frame value;

    @Override
    public ProcessStatus process(ByteBuffer bb) {
        if (state== State.DONE || state== State.ERROR) {
            throw new IllegalStateException();
        }
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
        if (internalbb.hasRemaining()){
            return ProcessStatus.REFILL;
        }
        state=State.DONE;
        internalbb.flip();
        switch (internalbb.get()) {
        	case 0 :
        		value = new Connection();
        		break;
        	case 1 : 
        		value = new AcceptedConnection();
        		break;
        	case 2 :
        		value = new RefusedConnection();
        		break;
        	case 3 :
        		value = new Message();
        	case 5 : 
        		value = new PM();
        }
        return ProcessStatus.DONE;
    }

    @Override
    public Frame get() {
        if (state!= State.DONE) {
            throw new IllegalStateException();
        }
        return value;
    }

    @Override
    public void reset() {
        state= State.WAITING;
        internalbb.clear();
    }
}
