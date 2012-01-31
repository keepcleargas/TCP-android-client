package com.betterchat.www;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;

import org.json.JSONException;
import org.json.JSONObject;

import android.os.Handler;
import android.os.Message;
import android.util.Log;



import com.betterchat.www.MessageTypes;
import com.betterchat.www.MessageStructures.GetNewMessages;
import com.betterchat.www.MessageStructures.GetOnlineUserList;
import com.google.gson.Gson;


public class Client implements Runnable {
	private Socket kkSocket;
	private byte[] buffer;
	private String Name;
	private Handler handle;
	private String ipadresse;
	enum RECEIVESTATE {WAITING, RECEIVING,ENDING};
	
	OutputStream outStream;
	InputStream instream;
	
	private String inputBuffer;
	private RECEIVESTATE receiveState;
	
	private volatile boolean stop = false;
	private boolean mIsPaused;
	
	public Client(String ipAddress, String name, Handler handle)
	{
		this.handle = handle;
		Name = name;
		buffer = new byte[1024];
		this.ipadresse = ipAddress;
		this.receiveState = RECEIVESTATE.WAITING;
	}
	
	public void connect()
	{
		try {
			kkSocket = new Socket(this.ipadresse , 8000);
			outStream = kkSocket.getOutputStream();
			instream = kkSocket.getInputStream();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
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
	        int bytes = instream.available();
	        if( bytes > 0 )
	        {
	        	 instream.read(buffer, 0, bytes);
	        	 String data = new String(buffer,0,bytes);
	        	 handleData(data);
	        }
	        
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
    
    private void handleData(String data)
    {
    	for (char c : data.toCharArray()) 
    	{
			switch (this.receiveState) {				
			case WAITING:
				if (c == 0x02)
				{
					this.receiveState=RECEIVESTATE.RECEIVING;
					this.inputBuffer="";
				}
				break;
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
			// TODO Auto-generated catch block
			e.printStackTrace();
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
    
    public void resumeUpdate(Handler handler) {
    	handle = handler;
    	mIsPaused = false;
    }

	@Override
	public void run() {
		while(!stop)
		{
			if(!mIsPaused) {
				Read();
			}
			try {
				Thread.sleep(1000);
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