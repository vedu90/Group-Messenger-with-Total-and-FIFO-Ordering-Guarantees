package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;

import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.LinkedList;
import android.net.Uri;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.Menu;
import android.widget.Button;
import android.widget.TextView;
import android.telephony.TelephonyManager;
import android.os.AsyncTask;

import java.io.BufferedReader;

import java.io.DataOutputStream;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;

import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.io.FileOutputStream;

import android.content.Context;

import android.util.Log;
import android.view.View;
import android.widget.EditText;

import java.net.ServerSocket;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 *
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {

    static final String REMOTE_PORT0 = "11108";
    static final String REMOTE_PORT1 = "11112";
    static final String REMOTE_PORT2 = "11116";
    static final String REMOTE_PORT3 = "11120";
    static final String REMOTE_PORT4 = "11124";
    ArrayList<String> remotePort = new ArrayList<String>(5);
    String myPort;
    static final int SERVER_PORT = 10000;
    static int messageCount = 0;
    static int sequenceNumber = 0;
    private static final String KEY_FIELD = "key";
    private static final String VALUE_FIELD = "value";
    static  Uri mUri;
    static int numOfConnections = 5;
    static int keySeqNum = 0;
    boolean checkFlag = false;
    Map<String,Integer> removedClient= new HashMap<String, Integer>();

    List<MessageDetails> holdBackQ = new ArrayList<MessageDetails>();
    static Map<String,TreeMap<Integer,Integer>> msgProposalMap = new HashMap<String,TreeMap<Integer,Integer>>();
    private ReentrantLock bMsgLock = new ReentrantLock();
    Map<Integer,Integer> receivedProposalCount= new HashMap<Integer, Integer>();
    Map<Integer,Integer> receivedSenders= new HashMap<Integer, Integer>();
    boolean clientRemoved = false;
    private static final Object Lock = new Object();
    private static final Object SecondLock = new Object();
    public class MessageDetails
    {
        public String msg;
        public String msgId;
        public String senderId;
        public String seqNum;
        public String suggestedProcess;
        public boolean deliveryStatus;
        MessageDetails(String s1,String s2,String s3,String n)
        {
            this.msg = s1;
            this.msgId = s2;
            this.senderId =  s3;
            this.seqNum = n;
            this.deliveryStatus = false;
            this.suggestedProcess = myPort;
        }
    }

    public class HoldBackQComparator implements Comparator<MessageDetails> {
        public int compare(MessageDetails m1, MessageDetails m2) {
            int seqNum1 = Integer.parseInt(m1.seqNum);
            int seqNum2 = Integer.parseInt(m2.seqNum);
            // Log.v("Comparison", "Comparing started "+seqNum1+" "+seqNum2);
            if(seqNum1 != seqNum2)
            {
                return seqNum1-seqNum2;
            }
            else
            {
                //   Log.v("Comparison", "Comparing delivery Status "+m1.deliveryStatus +" "+m2.deliveryStatus);

                if(m1.deliveryStatus == m2.deliveryStatus)
                {
                    int sProc1 = Integer.parseInt(m1.suggestedProcess);
                    int sProc2 = Integer.parseInt(m2.suggestedProcess);
                    //   Log.v("Comparison ", "Comparing messages "+sProc1+" "+sProc2);
                    return sProc1-sProc2;
                }
                else
                {
                    if(m1.deliveryStatus)
                    {
                        //          Log.v("Comparison", "Inside m1.deliveryStatus");
                        return 1;
                    }
                    //     Log.v("Comparison", "Outside m1.deliveryStatus");
                    return -1;
                }
            }
        }
    }

    private Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

        mUri = buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger2.provider");
        Log.v("onCreate", "onCreate started");
        remotePort.add(REMOTE_PORT0);
        remotePort.add(REMOTE_PORT1);
        remotePort.add(REMOTE_PORT2);
        remotePort.add(REMOTE_PORT3);
        remotePort.add(REMOTE_PORT4);
        Log.v("onCreate", "remote port added");
        removedClient.put(REMOTE_PORT0,1);
        removedClient.put(REMOTE_PORT1,2);
        removedClient.put(REMOTE_PORT2,3);
        removedClient.put(REMOTE_PORT3,4);
        removedClient.put(REMOTE_PORT4,5);
        Log.v("onCreate", "removedClient added");

        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */
        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());

        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));

        /*
         * TODO: You need to register and implement an OnClickListener for the "Send" button.
         * In your implementation you need to get the message from the input box (EditText)
         * and send it to other AVDs.
         */




        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        myPort = String.valueOf((Integer.parseInt(portStr) * 2));

        Log.v("myPort"+myPort, "myPort number is "+myPort);
        try {
            /*
             * Create a server socket as well as a thread (AsyncTask) that listens on the server
             * port.
             *
             * AsyncTask is a simplified thread construct that Android provides. Please make sure
             * you know how it works by reading
             * http://developer.android.com/reference/android/os/AsyncTask.html
             */
            Log.v("onCreate", "Checking server socket before ");
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            Log.v("onCreate", "Checking server socket ");
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
            Log.v("onCreate", "Checking server socket done ");
        } catch (IOException e) {

           /*  * Log is a good way to debug your code. LogCat prints out all the messages that
             * Log class writes.
             *
             * Please read http://developer.android.com/tools/debugging/debugging-projects.html
             * and http://developer.android.com/tools/debugging/debugging-log.html
             * for more information on debugging.
             */
            Log.v("OnCreate", "Can't create a ServerSocket "+e);
            return;
        }



        /*
         * Register an OnKeyListener for the input box. OnKeyListener is an event handler that
         * processes each key event. The purpose of the following code is to detect an enter key
         * press event, and create a client thread so that the client thread can send the string
         * in the input box over the network.
         */
        Log.v("OnCreate","Before on Click Listener");
        final Button button = (Button) findViewById(R.id.button4);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Log.v("OnCreate","Checking Click button");
                final EditText editText = (EditText) findViewById(R.id.editText1);
                Log.v("OnCreate","Checking edit text button");
                /*
                 * If the key is pressed (i.e., KeyEvent.ACTION_DOWN) and it is an enter key
                 * (i.e., KeyEvent.KEYCODE_ENTER), then we display the string. Then we create
                 * an AsyncTask that sends the string to the remote AVD.
                 */
                String msg = editText.getText().toString() + "\n";
                editText.setText(""); // This is one way to reset the input box.
                // TextView localTextView = (TextView) findViewById(R.id.local_text_display);
                //  localTextView.append("\t" + msg); // This is one way to display a string.
                //  TextView remoteTextView = (TextView) findViewById(R.id.remote_text_display);
                //  remoteTextView.append("\n");

                /*
                 * Note that the following AsyncTask uses AsyncTask.SERIAL_EXECUTOR, not
                 * AsyncTask.THREAD_POOL_EXECUTOR as the above ServerTask does. To understand
                 * the difference, please take a look at
                 * http://developer.android.com/reference/android/os/AsyncTask.html
                 */
                Log.v("OnCreate","Checking Client Task side");

                new ProposalTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,"START", msg, myPort);

                return;

            }
        });

        Log.v("OnCreate","Nothing Done");



    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }


    public void RemoveClient(String removedClientId)
    {
        try
        {
            //  String removedClientId = remotePort.get(connectionNum);
            Log.v("RemoveClient", "Client to be removed "+removedClientId);

        }
        catch (Exception e) {
            Log.v("RemoveClient", "Remove Client exception "+e);
        }





    }

    public synchronized void UpdateQueue()
    {
        Log.v("UpdateQueue","UpdateQueue modified again");
        //  if(holdBackQ.size()>15 || checkFlag == true)
        {
            //  checkFlag = true;
            Log.v("UpdateQueue","UpdateQueue started once again");
            new ContentProvider().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
        }

    }

    class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        private ReentrantLock pauseLock = new ReentrantLock();
        //    public GroupMessengerActivity activity;


        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];

            /*
             * TODO: Fill in your server code that receives messages and passes them
             * to onProgressUpdate().
             */
            try {

                //   pauseLock.lock();
                Log.v("ServerSide", "Lock Applied");

                //DataInputStream msgFromClient = new DataInputStream(clientSocket.getInputStream());
                while (true) {
                    //   pauseLock.lock();
                    Socket clientSocket = serverSocket.accept();
                    String message;
                    String[] splitMessage;
                    // for(int j = 0 ; j < numOfConnections ; j++)

                    Log.v("ServerSide", "Yet to read message ");
                    BufferedReader msgFromClient
                            = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                    message = msgFromClient.readLine();
                    if (message == null) {
                        Log.v("ServerSide", "Null message on Server Side");
                        //     pauseLock.unlock();
                        continue;
                    }
                    DataOutputStream responseMsg = new DataOutputStream(clientSocket.getOutputStream());

                    if (clientRemoved == false && message.equals("TEST")) {
                        Log.v("DummyTask", "Response message sent from DummyTask");
                        responseMsg.writeBytes("OK");
                        Thread.sleep(200);
                        msgFromClient.close();
                        clientSocket.close();
                        continue;
                    }
                    splitMessage = message.split("\\|");
                    Log.v("ServerSide", "Received Message "+message);
                    Log.v("ServerSide", "splitMessage[0] "+splitMessage[0]);

                    if(splitMessage[0].equals("BMULTICAST"))
                    {
                        if(removedClient.get(splitMessage[1]) == -1)
                        {
                            //    pauseLock.unlock();
                            continue;
                        }
                        synchronized (SecondLock)
                        {
                            sequenceNumber++;
                            Log.v("ServerSide", "splitMessage[0] again "+splitMessage[0]+" "+splitMessage[1]+" "+splitMessage[2]+" "+splitMessage[3]);
                            ProposalTaskAPI(splitMessage[3],splitMessage[2],splitMessage[1],sequenceNumber);
                        }


                        //   pTask.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, myPort);
                    }
                    else if(splitMessage[0].equals("PROPOSAL"))
                    {
                        Log.v("SenderSide", " Proposal Received : "+message);
                        if(removedClient.get(splitMessage[3]) == -1)
                        {
                            //    pauseLock.unlock();
                            continue;
                        }
                        FinalBMultiCastAPI(splitMessage[1],splitMessage[3],splitMessage[2]);
                    }
                    else if(splitMessage[0].equals("CONSENSUS"))
                    {
                        Log.v("SenderSide", " Consensus message : "+message);

                        String msgId = splitMessage[1];
                        String SenderId = splitMessage[2];
                        String suggestedSeqNum = splitMessage[3];
                        String suggestedProcess = splitMessage[4];

                        if(removedClient.get(SenderId) == -1)
                        {
                            //   pauseLock.unlock();
                            continue;
                        }



                        synchronized (SecondLock)
                        {
                            sequenceNumber = Math.max(sequenceNumber,Integer.parseInt(suggestedSeqNum));
                            for (MessageDetails s : holdBackQ)
                            {
                                if(s.msgId.equals(msgId) && s.senderId.equals(SenderId))
                                {
                                    s.suggestedProcess = suggestedProcess;
                                    s.seqNum = suggestedSeqNum;
                                    s.deliveryStatus = true;
                                    break;
                                }
                            }
                        }


                        UpdateQueue();



                    }
                    Log.v("ServerSide", "Message received on Server Side "+message);
                    Log.v("ServerSide","Putting Server to sleep");
                    //    Thread.sleep(500);
                    Log.v("ServerSide","Sleep over");
                    msgFromClient.close();
                    clientSocket.close();
                    //       pauseLock.unlock();
                }
            } catch (Exception e) {
                Log.v("ServerSide", "File write failed "+e);
                //   pauseLock.unlock();
            }


            return null;
        }



        public void ProposalTaskAPI(String msg, String msgCountNum, String senderId , int seqNum ) {
            Log.v("ProposalTask","PropProposalTask API");
            new ProposalTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,"PROPOSAL", msg, msgCountNum,senderId,Integer.toString(seqNum));
            return;
        }

        public void FinalBMultiCastAPI(String msgId, String senderId , String seqNum ) {
            Log.v("ProposalTask","FinalBMultiCastAPI API");
            new FinalBMultiCast().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "CONSENSUS",msgId, senderId,seqNum);
            return;
        }


        protected void onProgressUpdate(String...strings) {
            /*
             * The following code displays what is received in doInBackground().
             */
          /*  String strReceived = strings[0].trim();
            TextView remoteTextView = (TextView) findViewById(R.id.remote_text_display);
            remoteTextView.append(strReceived + "\t\n");
            TextView localTextView = (TextView) findViewById(R.id.local_text_display);
            localTextView.append("\n");

            /*
             * The following code creates a file in the AVD's internal storage and stores a file.
             *
             * For more information on file I/O on Android, please take a look at
             * http://developer.android.com/training/basics/data-storage/files.html
             */

           /* String filename = "GroupMessengerOutput123";
            String string = strReceived + "\n";
            FileOutputStream outputStream;

            try {
                outputStream = openFileOutput(filename, Context.MODE_PRIVATE);
                outputStream.write(string.getBytes());
                outputStream.close();
            } catch (Exception e) {
                Log.v("PublishProgress", "File write failed");
            }

            return;*/
        }


    }



    private class ProposalTask extends AsyncTask<String, Void, Void>
    {


        public void BMultiCastMessage(String msg)
        {
            int connectionNum = 0;
            try
            {
                bMsgLock.lock();

                for( ; connectionNum < numOfConnections ; connectionNum++)
                {
                    Log.v("ClientTask", "Modified BMultiCastMessage");
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(remotePort.get(connectionNum)));

                    DataOutputStream outputMsg = null;

                    socket.setSoTimeout(500);

                    Log.v("ClientTask", "Client Task debugging3");
                    outputMsg = new DataOutputStream(socket.getOutputStream());

                    if (outputMsg != null) {
                        Log.v("ClientTask", "BMulticast " + msg);
                        outputMsg.writeBytes(msg);
                    } else {
                        Log.v("ClientTask", "Client Task output msg null");
                        continue;
                    }
                    Thread.sleep(200);
                    outputMsg.close();
                    socket.close();
                }
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }
            catch (SocketTimeoutException ste) {
                String removeClientId = remotePort.get(connectionNum);
                Log.v("ClientSide", "Connection from "+removeClientId+" timed out");
                RemoveClient(removeClientId);
            }
            catch (UnknownHostException e) {
                Log.v("ClientSide", "ClientTask UnknownHostException");
            }
            catch(IOException e)
            {
                Log.v("ClientSide", "IOException");
            }
            bMsgLock.unlock();

        }


        protected Void doInBackground(String... msgs)
        {

            if(msgs[0].equals("START"))
            {
                Log.v("ClientTask","Client Task side initiated");
                messageCount++;
                String msg = "BMULTICAST";
                msg+="|";
                msg+=msgs[2];
                msg+="|";
                msg+=Integer.toString(messageCount);
                msg+="|";
                msg+=msgs[1];
                BMultiCastMessage(msg);
                return null;
            }

            else if(msgs[0].equals("PROPOSAL"))
            {
                String senderId = msgs[3];
                try
                {
                    Log.v("ProposalTask","Modified PropProposalTask testing");

                    String msg = msgs[1];
                    String msgCountNum = msgs[2];

                    String seqNum = msgs[4];

                    MessageDetails mObj = new MessageDetails(msg,msgCountNum,senderId,seqNum);
                    synchronized (SecondLock)
                    {
                        holdBackQ.add(mObj);
                    }

                    UpdateQueue();
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(senderId));

                    String msgSend = "PROPOSAL";
                    msgSend+="|";
                    msgSend+=msgCountNum;
                    msgSend+="|";
                    msgSend+=seqNum;
                    msgSend+="|";
                    msgSend+=myPort;

                    DataOutputStream outputMsg = null;

                    socket.setSoTimeout(500);

                    Log.v("ClientTask", "Client Task debugging3");
                    outputMsg = new DataOutputStream(socket.getOutputStream());

                    if (outputMsg != null) {
                        Log.v("ClientTask", "Proposal " + msgSend);
                        outputMsg.writeBytes(msgSend);
                    } else {
                        Log.v("ClientTask", "Client Task output msg null");
                    }
                    Thread.sleep(200);
                    outputMsg.close();
                    socket.close();

                    return null;
                }catch (SocketTimeoutException e) {
                    Log.v("SocketTimeoutException", "Timed out from client "+senderId);
                    RemoveClient(senderId);
                }
                catch (InterruptedException e) {
                    e.printStackTrace();
                }
                catch (UnknownHostException e) {
                    e.printStackTrace();
                } catch (SocketException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                return null;
            }
            else if(msgs[0].equals("CONSENSUS"))
            {

            }
            return null;
        }
    }

    private class FinalBMultiCast extends AsyncTask<String, Void, Void>
    {
        protected Void doInBackground(String... msgs)
        {
            synchronized (SecondLock)
            {
                Log.v("FinalBMultiCast", "Modified FinalBMultiCast Testing");
                int seqNum = Integer.parseInt(msgs[3]);
                int senderId = Integer.parseInt(msgs[2]);
                Log.v("FinalBMultiCast", "Received proposal from msg ID "+msgs[1]+" "+seqNum+" "+senderId);

                try
                {
                    if(clientRemoved==false)
                    {
                        String sId = msgs[2];
                        if(!receivedProposalCount.containsKey(Integer.parseInt(msgs[1])))
                        {
                            Log.v("receivedProposalCount", "receivedProposalCount "+msgs[1]+" "+removedClient.get(sId));
                            receivedProposalCount.put(Integer.parseInt(msgs[1]),1);
                            receivedSenders.put(Integer.parseInt(msgs[1]),removedClient.get(sId));
                        }
                        else
                        {
                            int val = receivedProposalCount.get(Integer.parseInt(msgs[1]));

                            receivedProposalCount.put(Integer.parseInt(msgs[1]),val+1);
                            Log.v("receivedProposalCount", "receivedProposalCount of "+msgs[1]+" "+val);
                            int s= receivedSenders.get(Integer.parseInt(msgs[1]));

                            s+=removedClient.get(sId);
                            Log.v("receivedSenders", "receivedSenders sum of "+msgs[1]+" "+s);
                            receivedSenders.put(Integer.parseInt(msgs[1]),s);
                            boolean temp = false;
                            int key = 0;
                            int num = 0;
                            if(receivedProposalCount.size() > 4)
                            {
                                for(Iterator<Map.Entry<Integer,Integer>> it = receivedProposalCount.entrySet().iterator(); it.hasNext(); ) {

                                    Map.Entry<Integer,Integer> v = it.next();
                                    Log.v("receivedProposalCount", "Inside receivedProposalCount "+v.getKey()+" "+v.getValue());
                                    if(v.getValue() == 5)
                                    {
                                        Log.v("receivedProposalCount", "receivedProposalCount 5");
                                        if(temp == true)
                                        {
                                            Log.v("receivedProposalCount", "receivedProposalCount 5 false");
                                            temp = false;
                                            break;
                                        }
                                    }
                                    else if(v.getValue() == 4)
                                    {
                                        Log.v("receivedProposalCount", "receivedProposalCount 4 true");
                                        key = v.getKey();
                                        num++;
                                        temp = true;
                                    }
                                    else
                                    {
                                        Log.v("receivedProposalCount", "receivedProposalCount false");
                                        temp = false;
                                        break;
                                    }
                                }

                                if(temp == true && num>1)
                                {
                                    boolean disconnected = false;
                                    int t = 15-receivedSenders.get(key);
                                    Log.v("receivedProposalCount", "Client to be removed "+t);

                                    String removedClientId = remotePort.get(t-1);

                                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                            Integer.parseInt(removedClientId));

                                    DataOutputStream outputMsg = null;
                                    try
                                    {
                                        socket.setSoTimeout(500);

                                        Log.v("ClientTask", "Client Task debugging3");
                                        outputMsg = new DataOutputStream(socket.getOutputStream());

                                        if (outputMsg != null) {
                                            Log.v("ClientTask", "Proposal TEST");
                                            outputMsg.writeBytes("TEST");
                                        } else {
                                            Log.v("ClientTask", "Client Task output msg null");
                                        }

                                        BufferedReader msgFromServer
                                                = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                                        String responseMsg = msgFromServer.readLine();
                                        if (responseMsg != null && responseMsg.equals("OK")) {
                                            Log.v("ClientTask", "Final Received response message from server " + removedClientId);
                                            disconnected = false;
                                        } else {
                                            disconnected = true;
                                            Log.v("ClientTask", "Final Null message from server " + removedClientId);}
                                    }
                                    catch (Exception e)
                                    {
                                        Log.v("Testing", "Testing checking exception " + e);
                                        disconnected = false;
                                    }



                                    if(disconnected == true)
                                    {
                                        clientRemoved = true;
                                        synchronized (SecondLock)
                                        {
                                            for (Iterator<MessageDetails> iterator = holdBackQ.iterator(); iterator.hasNext(); ) {
                                                MessageDetails element = iterator.next();
                                                if(element.senderId.equals(removedClientId))
                                                {
                                                    Log.v("RemoveClient", "Removing client Id from holdbackQ "+removedClientId);
                                                    iterator.remove();
                                                }
                                            }
                                        }



                                        Log.v("RemoveClient", "Modified Removing from msgProposalQ started");

                                        Log.v("RemoveClient", "Removing from msgProposalQ started");
                                        for(Iterator<Map.Entry<String, TreeMap<Integer,Integer>>> it = msgProposalMap.entrySet().iterator(); it.hasNext(); ) {
                                            Map.Entry<String, TreeMap<Integer,Integer>> entry = it.next();
                                            TreeMap<Integer,Integer> keyPair = entry.getValue();
                                            Log.v("RemoveClient", "Removing from msgProposalQ ");
                                            for(Iterator<Map.Entry<Integer,Integer>> it2 = keyPair.entrySet().iterator(); it2.hasNext();) {
                                                Integer sendId = it2.next().getKey();
                                                Log.v("RemoveClient", "Removing from msgProposalQ "+sendId+" "+removedClientId);
                                                if(sendId == Integer.parseInt(removedClientId))
                                                {
                                                    Log.v("RemoveClient", "Before removing size "+msgProposalMap.get(entry.getKey()).size());
                                                    it2.remove();
                                                    msgProposalMap.put(entry.getKey(),keyPair);
                                                    Log.v("RemoveClient", "After removing size "+msgProposalMap.get(entry.getKey()).size());
                                                    break;
                                                }
                                            }

                                        }



                                        remotePort.remove(removedClientId);
                                        removedClient.put(removedClientId,-1);
                                        numOfConnections--;
                                    }

                                }
                            }}

                    }
                }
                catch(Exception e)
                {
                    Log.v("Testing","Exception caught in new changes "+e);
                }

                if(!msgProposalMap.containsKey(msgs[1]))
                {
                    TreeMap<Integer,Integer> seqNumFrom = new TreeMap<Integer,Integer>();
                    seqNumFrom.put(senderId,seqNum);
                    msgProposalMap.put(msgs[1],seqNumFrom);
                }
                else
                {
                    TreeMap<Integer,Integer> seqNumFrom = msgProposalMap.get(msgs[1]);
                    seqNumFrom.put(senderId,seqNum);
                    msgProposalMap.put(msgs[1],seqNumFrom);
                    Log.v("FinalBMultiCast", "seqNumFrom Size "+seqNumFrom.size());


                    for(Iterator<Map.Entry<String, TreeMap<Integer,Integer>>> it = msgProposalMap.entrySet().iterator(); it.hasNext(); ) {
                        Map.Entry<String, TreeMap<Integer,Integer>> val = it.next();
                        TreeMap<Integer,Integer> keyPair = val.getValue();
                        if(keyPair.size() == numOfConnections)
                        {

                            Log.v("FinalBMultiCast", "Received proposal from all avd's for msg ID "+val.getKey());
                            int maxSeqNum = -1;
                            int minID = -1;
                            for(Map.Entry<Integer,Integer> entry : keyPair.entrySet()) {
                                Integer sendId = entry.getKey();
                                Integer seqValue = entry.getValue();
                                if(seqValue > maxSeqNum)
                                {
                                    maxSeqNum = seqValue;
                                    minID = sendId;
                                }
                                else if(seqValue == maxSeqNum)
                                {
                                    if(sendId < minID)
                                    {
                                        minID = sendId;
                                    }
                                }
                            }
                            Log.v("FinalBMultiCast", "Final agreed ID "+minID+" "+maxSeqNum);
                            String msg="CONSENSUS";
                            msg+="|";
                            msg+=val.getKey();
                            msg+="|";
                            msg+=myPort;
                            msg+="|";
                            msg+=Integer.toString(maxSeqNum);
                            msg+="|";
                            msg+=Integer.toString(minID);
                            BMultiCastMessage(msg);
                            it.remove();

                        }
                    }


                }

                return null;
            }


        }

        public void BMultiCastMessage(String msg)
        {
            int connectionNum = 0;
            try
            {
                //    bMsgLock.lock();

                for( ; connectionNum < numOfConnections ; connectionNum++)
                {
                    Log.v("ClientTask", "Modified BMultiCastMessage");
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(remotePort.get(connectionNum)));

                    DataOutputStream outputMsg = null;

                    socket.setSoTimeout(500);

                    Log.v("ClientTask", "Client Task debugging3");
                    outputMsg = new DataOutputStream(socket.getOutputStream());

                    if (outputMsg != null) {
                        Log.v("ClientTask", "BMulticast " + msg);
                        outputMsg.writeBytes(msg);
                    } else {
                        Log.v("ClientTask", "Client Task output msg null");
                        continue;
                    }
                    Thread.sleep(100);



             /*  BufferedReader msgFromServer
                        = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String responseMsg = msgFromServer.readLine();
                if (responseMsg != null && responseMsg.equals("OK")) {
                    Log.v("ClientTask", "Final Received response message from server " + remotePort.get(connectionNum));

                } else {
                    Log.v("ClientTask", "Final Null message from server " + remotePort.get(connectionNum));}*/


                    outputMsg.close();

                    socket.close();


                }
            }
            catch (SocketTimeoutException ste) {
                String removeClientId = remotePort.get(connectionNum);
                Log.v("ClientSide", "Connection from "+removeClientId+" timed out");
                RemoveClient(removeClientId);
            }
            catch (UnknownHostException e) {
                Log.v("ClientSide", "ClientTask UnknownHostException");
            }
            catch(IOException e)
            {

            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }
            //  bMsgLock.unlock();

        }
    }

    private class ContentProvider extends AsyncTask<String, Void, Void> {
        protected Void doInBackground(String... msgs) {
            synchronized (SecondLock)
            {
                try
                {

                    Collections.sort(holdBackQ, new HoldBackQComparator());

                    Log.v("UpdateQueue", "Sorted Queue Starts");
                    for (MessageDetails element : holdBackQ) {
                        Log.v("UpdateQueue", "Sorted Queue "+element.seqNum+" "+element.msgId+" "+element.senderId+" "+element.suggestedProcess+" "+element.deliveryStatus);
                    }
                    Log.v("UpdateQueue", "Sorted Queue ends");
                    //  if(holdBackQ.size()>=20 || checkFlag == true)
                    {
                        checkFlag = true;
                        for (Iterator<MessageDetails> iterator = holdBackQ.iterator(); iterator.hasNext(); ) {
                            MessageDetails element = iterator.next();
                            Log.v("DeliveryMessage", "To deliver message "+element.deliveryStatus+" "+element.seqNum);

                            if(removedClient.get(element.senderId)==-1)
                            {
                                Log.v("DeliveryMessage", "Removing inside Content provider "+element.seqNum+" "+element.msgId+" "+element.senderId);
                                iterator.remove();
                            }
                            else if(element.deliveryStatus)
                            {
                                ContentValues cv = new ContentValues();
                                Log.v("DeliveryMessage", "Delivered Message with seq Num "+keySeqNum+" "+element.msg+" msgId "+element.msgId+" seqNum "+element.seqNum+" suggested Process "+element.suggestedProcess);
                                cv.put(KEY_FIELD,Integer.toString(keySeqNum));
                                cv.put(VALUE_FIELD,element.msg);
                                getContentResolver().insert(mUri, cv);
                                iterator.remove();
                                keySeqNum++;
                            }
                            else
                            {

                                break;
                            }
                        }
                    }

                }

                catch (Exception e) {
                    Log.v("PublishProgress", "ContentProvider exception caught "+e);
                }


                return null;
            }
        }



    }

}
