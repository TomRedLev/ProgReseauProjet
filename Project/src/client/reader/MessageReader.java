package fr.upem.net.tcp.nonblocking.reader;

import java.nio.ByteBuffer;

import fr.upem.net.tcp.nonblocking.frame.Message;



public class MessageReader implements Reader<Message> {

    private enum State {DONE,WAITINGLOGIN,WAITINGMESSAGE,ERROR};

    private final StringReader reader = new StringReader();
    private State state = State.WAITINGLOGIN;
    private Message msg = new Message();

    @Override
    public ProcessStatus process(ByteBuffer bb) {
    	switch (state) {
	    	case WAITINGLOGIN :
	    		switch(reader.process(bb)) {
		    		case DONE :
		    			msg.setLogin(reader.get());
		    			reader.reset();
		    			state = State.WAITINGMESSAGE;
		    			break;
		    		case ERROR : 
		    			state = State.ERROR;
		    			return ProcessStatus.ERROR;
		    		case REFILL :
		    			return ProcessStatus.REFILL;
		    			
	    		}
	    	case WAITINGMESSAGE : 
	    		switch (reader.process(bb)) {
	    			case DONE : 
	    				msg.setStr(reader.get());
	    				state = State.DONE;
	    				return ProcessStatus.DONE;
	    			case ERROR :
	    				state = State.ERROR;
	    				return ProcessStatus.ERROR;
	    			case REFILL :
	    				return ProcessStatus.REFILL;
	    		}
	    	default :
	    		throw new IllegalStateException();
    	}
    }

    @Override
    public Message get() {
    	if (state!= State.DONE) {
            throw new IllegalStateException();
        }
        return msg;
    }

    @Override
    public void reset() {
        state= State.WAITINGLOGIN;
        reader.reset();
    }
}
