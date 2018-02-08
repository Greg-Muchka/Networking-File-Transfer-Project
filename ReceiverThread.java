import java.io.IOException;
import java.net.DatagramPacket;

public class ReceiverThread extends Thread{
    
    private FastFtp instance;
    public ReceiverThread(FastFtp obj){
	this.instance = obj;
    }
    public void run()
    {
	// while not terminated :
	while(!instance.shutdown)
	    {
		try{
		    byte[] receiveData = new byte[Segment.MAX_SEGMENT_SIZE];
		    DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
		    //receive a DatagramPacket pkt from UDP socket
		    instance.d_socket.receive(receivePacket); 
		    //call processAck(new Segment(pkt)) 
		    instance.processACK(new Segment(receivePacket));
		} catch (IOException e) {
		    e.printStackTrace();
		} catch (InterruptedException e) {
		    e.printStackTrace();
		}
	    }
    }
}
