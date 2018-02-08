import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Timer;
/**
 * 
 * FastFtp class
 * 
 * @author 	Gregory Muchka (Student ID: 10153582)
 * Date:	November 22, 2016
 * 
 */
public class FastFtp {

	private int timeout;
	private TxQueue queue = null;
	private String serverName;
	private int serverPort;
	private Timer timer;
	public boolean shutdown = false;
	public DatagramSocket d_socket;
	/*
	 * FastFtp Constructor
	 * @param int window: size of window
	 * @param int timeout: time in milliseconds 
	 */
	
	public FastFtp(int window, int timeout)
	{
		this.timeout = timeout;
		this.queue = new TxQueue(timeout);
	}
	
	/* 
	 * send(String, int, String) 
	 * 
	 * @param String serverName
	 * @param int serverPort
	 * @param String filename
	 * 
	 * @returns void
	 * 
	 * @throws UnknownHostException, IOException, InterruptedException
	 */
	public void send(String serverName, int serverPort, String fileName) throws UnknownHostException, IOException, InterruptedException
	{	
		//save serverName and serverPort to be used again in processSend()
		this.serverName = serverName;
		this.serverPort = serverPort; 
		
		File file = new File(fileName);
		//check to see if the file is there
		if(file.exists())
		{
			//reading file data into byte array
	    	FileInputStream input = new FileInputStream(file);
	    	byte[] data = new byte[input.available()];
			input.read(data);
			
			//setting up TCP 3 way handshake
			Socket socket = new Socket(serverName, serverPort);
			this.d_socket = new DatagramSocket(socket.getLocalPort()); //creating DatagramSocket to be used elsewhere
			
			DataOutputStream out = new DataOutputStream (socket.getOutputStream());
			out.writeUTF(fileName);
			
			//read from InputStream if server access is okay 
			InputStream in = socket.getInputStream();
			if(in.read() == 0)
			{
				//start ReceiverThread to receive acks
				ReceiverThread receiver = new ReceiverThread(this);
		    	receiver.start();
		    	
		    	int seq_num = 0, index = 0, prefix = 0;
		    	//while the entire file has not been parsed into segments
		    	while(index < data.length){
		    		
		    		prefix = seq_num*Segment.MAX_PAYLOAD_SIZE;//start of data segment
		    		index = ((seq_num + 1)*Segment.MAX_PAYLOAD_SIZE);//end of data segment
		    	
		    		//index is greater then the data length send a smaller data segment
		    		if(index >= data.length)
		    			index = data.length;
		    		
		    		byte [] seg_data_array = new byte[index - prefix];
		    		seg_data_array = Arrays.copyOfRange(data, prefix, index);
		    		Segment seg = new Segment(seq_num, seg_data_array);
					
		    		//busy wait until room in queue
					while(queue.isFull());
					processSend(seg);
					queue.add(seg);// add seg to the transmission queue txQueue
					
					seq_num++;	
		    	}
		    	while (!queue.isEmpty());
		    	
		    	//cancel timer, ACK receiving thread, clean up
		    	timer.cancel();
		    	//call to stop ReceiverThread
		    	shutdown();
		    	//close connection to server
		    	out.writeByte(0);
			}
			else
				System.out.println("Server could not be reached.");
			
			d_socket.close();
	    	socket.close();
			in.close();
            out.close();
            input.close();
		}
		else
			System.out.println("File could not be found in Current Directory");
	}
	
	/*
	 * processSend(Segment)
	 * 
	 * @param Segment seg
	 * @return void
	 * @throws InterruptedException, IOException
	 */
 	public synchronized void processSend(Segment seg) throws InterruptedException, IOException{
		// send seg to the UDP socket
        InetAddress IPAddress = InetAddress.getByName(serverName);
        DatagramPacket sendPacket = new DatagramPacket(seg.getBytes(), seg.getBytes().length, IPAddress, serverPort);
        d_socket.send(sendPacket);
		// if txQueue.size() == 1, start the timer
		if(queue.size() == 1)
		{
			timer = new Timer(true);
			timer.schedule(new TimeoutHandler(this), timeout);
		}	

	}
 	/*
 	 * processSck(Segment)
 	 * 
 	 * @param Segment ack
 	 * @retrun void
 	 * @throws InterruptedException
 	 */
	public synchronized void processACK(Segment ack) throws InterruptedException{
		// if ACK not in the current window, do nothing
		Segment[] segArray = queue.toArray();
		boolean in_window = false;
		
		for (int i = 0; i < segArray.length; i++) {
			if(segArray[i].getSeqNum() <= ack.getSeqNum())
				in_window = true;
		}
		
		if(in_window)
			timer.cancel();
		
		// while txQueue.element().getSeqNum() < ack.getSeqNum()
		for(int j = 0; j < segArray.length && segArray[j].getSeqNum() < ack.getSeqNum(); j++)
			queue.remove();
		
		if(!queue.isEmpty())
		{
			timer = new Timer(true);
			timer.schedule(new TimeoutHandler(this), timeout);
		}	
		
	}
	/*
	 * processTimeout()
	 * 
	 * @return void
	 * @throws InterruptedException, IOException
	 */
	public synchronized void processTimeout () throws InterruptedException, IOException{
		// get the list of all pending segments by calling txQueue.toArray()
		Segment[] segArray = queue.toArray();
		// go through the list and send all segments to the UDP socket
		for (int i = 0; i < segArray.length; i++)
			processSend(segArray[i]);
		
		// if not txQueue.isEmpty(), start the timer
		if(!queue.isEmpty())
		{		
			timer = new Timer(true);
			timer.schedule(new TimeoutHandler(this), timeout);
		}	
	}
	/*
	 * shutdown()
	 * 
	 * @return void
	 * use of this function is for shutting down any currently running threads
	 */
	public void shutdown()
	{
		this.shutdown = true;
	}
}
