/* My implementation is based on a Client-Server Programming.
   This class represents the Server.
   The Master reads the input file, gets requests from available Minions
   and divides the the cracking workload between them. */

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Vector;

public class Master {

    static final int portNumber = 8080; // Master is listening on this port
    static String hashPassword; // the current hash that the minions are trying to crack
    static int range; // used to determine a range of potential passwords
    static boolean foundPassword = false; // true if the current hash was cracked, false otherwise
    static Vector<String> hashPasswords = new Vector<>(); // stores all the hashes from the input file
    static int indexHash = 0; // the index of the current hash in the vector
    static boolean toBreak = false; // true if all the passwords were cracked, false otherwise

    public static void main(String []args) throws IOException {
        System.out.println("********* Master *********");
        String line; BufferedReader buffered = null; PrintStream printer = null; boolean validInput = true;
        int minionID = 0; // counter for Minions

        ServerSocket serverSocket = new ServerSocket(portNumber);

        /* assumption: the name of the input file is given in the command line,
           and the file is located in the same directory as the program */
        FileReader file = new FileReader(args[0]);
        try (BufferedReader br = new BufferedReader(file)) {
            while ((line = br.readLine()) != null) { // read a hash from the file and add it to the hashes vector
                if(!isValidInput(line)) {
                    System.out.println("The input is invalid, please correct and try again");
                    validInput = false;
                    break;
                }
                hashPasswords.add(line);
            }
        }
        if(validInput) {
            hashPassword = hashPasswords.elementAt(0);
            range = -1;

            while (!toBreak) {
                Socket clientSocket = serverSocket.accept(); // accept the incoming request
                buffered = new BufferedReader(new InputStreamReader(clientSocket.getInputStream())); // for reading
                printer = new PrintStream(clientSocket.getOutputStream()); // for writing
                MinionHandler minion = new MinionHandler(clientSocket, minionID++, buffered, printer); // create a new minion
                Thread thread = new Thread(minion);
                thread.start();
            }
            System.out.println("All passwords were cracked!");
        }
        closeMaster(serverSocket, buffered, printer);
    }

    // check if a hash given in the input file is valid, i.e has 32 characters, and is in hexadecimal
    public static boolean isValidInput(String hash) {
        int len = hash.length();
        if(len != 32)
            return false;
        for(int i = 0; i < len; i++) {
            if(Character.digit(hash.charAt(i), 16) == -1) // check if hex
                return false;
        }
        return true;
    }

    // close the master and disconnect
    public static void closeMaster(ServerSocket serverSocket, BufferedReader buffered, PrintStream printer) throws IOException {
        if(buffered != null)
            buffered.close();
        if(printer != null)
            printer.close();
        serverSocket.close();
    }


    // A class for handling multiple Minions
    public static class MinionHandler implements Runnable {
        Socket clientSocket;
        int clientID;
        BufferedReader buffered;
        PrintStream printer;
        String sendInfo;
        String receiveResult;

        // a builder
        MinionHandler(Socket socket, int id, BufferedReader buffered, PrintStream printer) {
            this.clientSocket = socket;
            this.clientID = id;
            this.buffered = buffered;
            this.printer = printer;
        }

        @Override
        public void run() {
            while (!toBreak) {
                range++;
                if(foundPassword) {
                    indexHash++;
                    if(indexHash == hashPasswords.size()) {
                        toBreak = true;
                        break;
                    }
                    hashPassword = hashPasswords.elementAt(indexHash);
                    range = 0;
                    foundPassword = false;
                }

                sendingInfo();
                try {
                    receiveResult = buffered.readLine();
                }
                catch (IOException e) { // handling with minion crashing
                    try {
                        this.clientSocket.close();
                        range--;
                        break;
                    } catch (IOException ioException) {
                        ioException.printStackTrace();
                    }
                }

                if (!receiveResult.equals("fail")) {
                    foundPassword = true;
                    printingResults(receiveResult);
                }
            }
            try {
                this.clientSocket.close();
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }

        // send to minion the current hash to crack and a new range to search in
        public void sendingInfo() {
            sendInfo = hashPassword + "\n" + range;
            printer.println(sendInfo);
            System.out.println("Sending hash " + (indexHash+1) + " to Minion " + clientID + ", range number: " + range);
        }

        // print a successful result to the master
        public void printingResults(String receiveResult) {
            System.out.println("Hash " + (indexHash+1) +" was cracked successfully by Minion " + clientID);
            System.out.println("The original password is: " + receiveResult + "\n");
        }
    }
}


