package com.betterchat.www;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import org.json.JSONException;
import org.json.JSONObject;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.betterchat.www.MessageStructures.GetNewMessages;
import com.betterchat.www.MessageStructures.GetOnlineUserList;
import com.betterchat.www.MessageStructures.NewUserOnline;
import com.google.gson.Gson;


public class Client implements Runnable {
	private Socket kkSocket;
	private byte[] buffer;
	private String Name;
	private Handler handle;
	private String ipadresse;
	private final long TIMEOUT = 60*1000;
	private long dataReceivedTime=0;
	enum RECEIVESTATE {WAITING, RECEIVING,ENDING};
	
	OutputStream outStream;
	InputStream instream;
	
	private String inputBuffer;
	private RECEIVESTATE receiveState;
	
	private volatile boolean stop = false;
	private boolean mIsPaused;
	
	public Client(String ipAddress, String name)
	{
		Name = name;
		buffer = new byte[1024];
		this.ipadresse = ipAddress;
		this.receiveState = RECEIVESTATE.WAITING;

	}
	
	public boolean connect()
	{
		try {
			this.dataReceivedTime = System.currentTimeMillis();
			kkSocket = new Socket();//this.ipadresse , 8000
			kkSocket.connect(new InetSocketAddress(this.ipadresse , 8000), 10000);
			outStream = kkSocket.getOutputStream();
			instream = kkSocket.getInputStream();

		} catch (UnknownHostException e) {
			Log.d("connect", "Host not available");
			e.printStackTrace();
			return false;
		} catch (IOException e) {
			Log.d("connect", "Could not connect");
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	public void sendUserLogon()
	{
		UserLogon user = new UserLogon();
		user.username=Name;
		Gson gson = new Gson();
		String message = gson.toJson(user);
		SendMessage(message);
	}
	
    public void Read()
    {
		try {
			if (this.TIMEOUT < System.currentTimeMillis() - this.dataReceivedTime)
			{
				sendKeepAlive();
				this.dataReceivedTime = System.currentTimeMillis();
			}
	        int bytes = instream.available();
	        if( bytes > 0 )
	        {
	        	 instream.read(buffer, 0, 1024);
	        	 String data = new String(buffer,0,1024);
	        	 handleData(data);
	        	 this.dataReceivedTime = System.currentTimeMillis();
	        }
	        
		} catch (IOException e) {
			Log.d("Read","Connection lost");
			e.printStackTrace();
		}
    }
    
    private void sendKeepAlive() {
		try
		{
			if (!kkSocket.isConnected())
				Log.d("sendKeepAlive","isConnected returned false");
			if (kkSocket.isOutputShutdown())
				Log.d("sendKeepAlive","isOutputShutdown returned true");
			this.outStream.write(0x03);
		}
		catch (IOException e) {
			Log.d("sendKeepAlive","sending something for keep alive");
		}
		
	}

	private void handleData(String data)
    {
    	for (char c : data.toCharArray()) 
    	{
    		if (c== 0x02)
    		{
    			this.receiveState=RECEIVESTATE.RECEIVING;
				this.inputBuffer="";
				continue;
    		}
			switch (this.receiveState) {				
			case RECEIVING:
				if (c==0x10)
					this.receiveState=RECEIVESTATE.ENDING;
				else
					this.inputBuffer = this.inputBuffer + c;
				break;

			case ENDING:
				if (c==0x03)
				{	
					String completeMessage= this.inputBuffer;
					handleMessage(completeMessage);
				}
				this.receiveState=RECEIVESTATE.WAITING;
				break;

			default:
				break;
			}
		}
    }
    
    private void handleMessage(String message)
    {
    	int id=0;
		try {
			id = Integer.parseInt(new JSONObject(message).get("type").toString());
		} catch (NumberFormatException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		Message mess = new Message();
    	switch (id) 
    	{
		case MessageTypes.USERLOGON:
			Log.d("handleMessage", "USERLOGON");
			//TODO do something with the message
			break;
		case MessageTypes.PUBLISHMESSAGE:
			Log.d("handleMessage", "PUBLISHMESSAGE");
			//TODO do something with the message
			PublishMessage pubMessage = new Gson().fromJson(message, PublishMessage.class);
			mess.what=MessageTypes.PUBLISHMESSAGE;
			mess.obj = pubMessage;
			handle.sendMessage(mess);
			break;
		case MessageTypes.GETONLINEUSERLIST:
			Log.d("handleMessage","GETONLINEUSERLIST");
			GetOnlineUserList userList= new Gson().fromJson(message, GetOnlineUserList.class);
			mess.what=MessageTypes.GETONLINEUSERLIST;
			mess.obj = userList;
			handle.sendMessage(mess);
			break;
		case MessageTypes.NEWUSERONLINE:
			Log.d("handleMessage","NEWUSERONLINE");
			NewUserOnline userOnline = new Gson().fromJson(message, NewUserOnline.class);
			mess.what=MessageTypes.NEWUSERONLINE;
			mess.obj = userOnline;
			handle.sendMessage(mess);
		default:
			break;
		}
    	
    }
    
    public void SendMessage(String msg)
    {
    	byte[] startByte= {0x02};
    	byte[] endByte= {0x10,0x03};
        byte[] msgByte = msg.getBytes();
        
        try {
        	outStream.write(startByte, 0, startByte.length);
			outStream.write(msgByte, 0, msgByte.length);
			outStream.write(endByte, 0, endByte.length);
			outStream.flush();
		} catch (IOException e) {
			Log.d("sendMessage", "IOException caucht. Connection properly lost");
			//TODO connection properly lost. Maybe reconnect
		}
    }
    
    public void getNewMessages(long lastTimestamp)
    {
    	GetNewMessages msg = new GetNewMessages();
    	msg.receiver="all";
    	msg.lastSeenTimeStamp=lastTimestamp;
    	SendMessage(new Gson().toJson(msg));
    }
    
    public synchronized void Start()
    {
    	Thread listenThread = new Thread(this);
    	listenThread.start();
    }
    
    public void pauseUpdate() {
    	mIsPaused = true;
    }
    
    public void resumeUpdate() {
    	mIsPaused = false;
    }
    
    public void setHandler(Handler handler) {
    	this.handle = handler;
    }

	@Override
	public void run() {
		while(!stop)
		{
			if(!mIsPaused) {
				Read();
			}
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		Shutdown();
	}
	
	private void Shutdown()
	{
    	try {
			kkSocket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
    	kkSocket = null;
	}
    
    public synchronized void Stop()
    {
    	stop = true;
    }
}
