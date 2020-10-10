import java.net.*;
import java.io.*;
import java.lang.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.ArrayList;

public class TCPServer {
	static private ConcurrentHashMap<Integer, String> factorList = null;
	
	public static void main(String[] args) throws Exception {
		ServerSocket server;
		ExecutorService threadPool; 
		try {
			server = new ServerSocket(7896);
			threadPool = Executors.newFixedThreadPool(3); //Uses ExecutorService to set maximum number of client threads
			int counter = 0; //Client number counter
			System.out.println("Server Started.");
			
			//Loop for recieveing client connections
			while (true) {
				counter++; //Increments counter every time a client connects
				Socket client = server.accept();
				threadPool.execute(new clientThread(client, counter)); //Starts new client thread from pre-allocated thread pool
				System.out.println("Client Number: " + counter + ", connected.");
			}
		} catch(Exception e) {
			System.out.println("Connection: " + e.getMessage());
		}
	}
	
	/*
	* Method to retrieve the Concurrent Hashmap used to store already calculated factors
	* Utilizes a singleton style method to ensure only one istance of the hashmap is used during server runtime
	*/
	@SuppressWarnings("unchecked") //Supresses warning for casting type Object to ConcurrentHashMap, not possible to check before casting.
	static public ConcurrentHashMap<Integer, String> getFactorMap() {
		if (factorList == null) {
			//Creates a file instance of factorHashmap.ser file, used to check if the file exists.
			File f = new File("factorHashmap.ser");
			if (f.isFile()) {
				//If file does exist, uses ObjectInputStream to read file into the concurrent hashmap factorList.
				System.out.println("FactorHashmap.ser found, reading from Hashmap");
				try {
					FileInputStream in = new FileInputStream("FactorHashmap.ser");
					ObjectInputStream oin = new ObjectInputStream(in);
					factorList = (ConcurrentHashMap<Integer, String>)oin.readObject();
					in.close();
					oin.close();
				} catch (ClassNotFoundException c) {
					c.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			else {
				//If file does not exist, create a new concurrent hashmap at factorList
				System.out.println("FactorHashmap.ser not found, creating new Hashmap");
				factorList = new ConcurrentHashMap<Integer, String>();
			}
		}
		return factorList;
	}
	
	
	/*
	* Method to save the hashmap to FactorHashmap.ser
	*/
	static public void saveFactorMap() {
		System.out.println("Saving Factor HashMap to FactorHashmap.ser");
		try {
			FileOutputStream out = new FileOutputStream("FactorHashmap.ser");
			ObjectOutputStream objout = new ObjectOutputStream(out);
			objout.writeObject(factorList);
			objout.close();
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
 
class clientThread implements Runnable {
	final int THREAD_DURATION = 600; //in seconds
	Socket client;
	int clientNo;
	long lastInputTime = System.currentTimeMillis(); //Time at which thread was last used by user
	String responseMode = ""; //Variable for server response type
	ConcurrentHashMap<Integer, String> factorList = TCPServer.getFactorMap(); //Gets the factor ConcurrentHashMap from the server

	clientThread(Socket clientSocket, int counter) {
		client = clientSocket;
		clientNo = counter;
	}
	
	public void run() {
		try {
			//Socket IO variables
			//DataInputStream i = new DataInputStream(client.getInputStream());
			BufferedReader r = new BufferedReader(new InputStreamReader(client.getInputStream()));
			PrintStream w = new PrintStream(client.getOutputStream(), true);
			
			//Server response and user input variables
			String response, stringUserInput;
			
			//Atomic variables to comminicate between the main client thread, and the input thread
			AtomicReference<String> inputLine = new AtomicReference<String>(null); //Stores user input so both threads can access
			AtomicBoolean flag = new AtomicBoolean(true); //Flag so the client thread can stop the input thread
			//Locks used to syncronize client and input threads
			AtomicBoolean lock1 = new AtomicBoolean(false); 
			AtomicBoolean lock2 = new AtomicBoolean(false);
			
			/*
			* Thread for user input, allows the timer to continue counting
			*/
			Thread inputThread = new Thread() {
				public void run() {
					String tempInput;
					while (flag.get()) { //Looping when flag is true
						try {
							tempInput = r.readLine(); //Reads input from DataInputStream
							inputLine.set(tempInput); //Sets the AtomicRefrence inputLine to what was just read.
							
							//(1) Sets the locks to syncronize both threads. Ensures that main thread reads the users input only once
							lock1.set(true); lock2.set(true);
							while (lock1.get()); //(2) Loops until main thread releases lock one 
							inputLine.set(null); //Sets the inputLine AtomicRefrence to null once main thread has read the user's input
							lock2.set(false); //(5) Releases lock on client thread
							
						} catch(IOException e) {}
					}
				}
			};
			inputThread.start();
			
			/*
			* Main loop for client thread
			*/
			while(checkElapsedTime()) { //Loops while checkElapsedTime is true
				stringUserInput = inputLine.get(); //Gets the current value of the AtomicReference inputLine every loop
				
				//Initiates a save if it needs to
				//TCPServer.saveTimer();
				
				//If the user input is not null (only happens once per input because of lock)
				if (stringUserInput != null) {
					//Updates last used time when user sends new input
					lastInputTime = System.currentTimeMillis();
					//Checks if the user entered the exit command
					if (stringUserInput.equals("exit")) {
						System.out.println("Exit command detected.");
						TCPServer.saveFactorMap();
						flag.set(false); //Sets flag for input thread to false, stopping the thread
						break;
					}
					//Checks if user entered server shutdown command
					else if (stringUserInput.equals("shutdown")) {
						System.out.println("Shutdown command detected.");
						TCPServer.saveFactorMap();
						System.exit(0);
					}
					response = serverResponse(stringUserInput); //Sends user's input to method for server response
					System.out.println("Client " + clientNo + " Response: " + response);
					w.println(response); //Send response to client socket using PrintStream
					w.flush();
				}
				
				
				
				//If lock one is enabled (i.e. user just input something)
				if (lock1.get()) {
					lock1.set(false); //(3) Releases the lock on input thread
					while (lock2.get()); //(4) Waits until input thread sets AtomicReference inputLine back to null
				}
			}

			//Closes open connections
			r.close();
			w.close();
			client.close();
			
		} catch(Exception e) {
			System.out.println(e);
		} finally {
			System.out.println("Client Number: " + clientNo + ", disconnected.");
		}
	}
	
	/*
	* Handles user input with series of switch statements.
	*/
	public String serverResponse(String userInput) {
		String finalResponse = "";
		switch(userInput) {
			case "equation":
				responseMode = "equation";
				finalResponse = "Server response type set to 'equation'";
				break;
			case "factor":
				responseMode = "factor";
				finalResponse = "Server response type set to 'factor'";
				break;
			default:
				switch(responseMode) {
					case "equation":
						finalResponse = computeEquation(userInput);
						break;
					case "factor":
						finalResponse = computeFactor(userInput);
						break;
					default:
						finalResponse = "Server response type not set";
						break;
				}
		}
		return finalResponse;
	}
	
	/*
	* Method for the equation solving function of the server
	*/
	public String computeEquation(String input) {
		//List of valid numbers / smybols for equations
		final char[] VALID_NUMBERS = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9'}; 
		final char[] VALID_SYMBOLS = {' ', '+', '-', '*', '/', '.'};
		char[] inputEquation = input.toCharArray(); //Takes input string and converts to array of characters
		ArrayList<String> processedEquation = new ArrayList<String>();
		String currentItem = "";
		String equationResponse = ""; 
		//Checks if the user actually input something
		if (inputEquation.length == 0)
			equationResponse = "Invalid Input";
		//Checks that the first and last character of input is a valid integer.
		else if (!contains(inputEquation[0], VALID_NUMBERS) || !contains(inputEquation[inputEquation.length-1], VALID_NUMBERS))
			equationResponse = "Invalid Input";
		else {
			//Loops through each character in initial input string
			for (int i = 0; i<inputEquation.length; i++) { 
				//If the current character is one of the predefined valid inputs
				if (contains(inputEquation[i], VALID_NUMBERS) || contains(inputEquation[i], VALID_SYMBOLS)) {
					//If current character is space
					if (inputEquation[i] == VALID_SYMBOLS[0]) {
						processedEquation.add(currentItem);
						currentItem = "";
					}
					//If current character is the last in the input string
					else if (i == (inputEquation.length-1)) {
						currentItem += inputEquation[i];
						processedEquation.add(currentItem);
					}
					else
						currentItem += inputEquation[i]; 
				}
				//If current character is not valid, return error message
				else {
					equationResponse = "Invalid Input";
					break;
				}
			}
		}
		
		//If previous set of if / else did not find any errors in the equation
		if (!equationResponse.equals("Invalid Input")) {
			boolean stepComplete = false;
			/*
			* While the array list remains unsolved, loops through the array checking for different operators
			* There is one for loop per operator, in the order that it should be calculated (* / then + -)
			* When one of the loops finds it's operator, it takes the number before and after in the array, and applies the operator
			* The result of this operation is then set to the location in the array of the operator
			* The boolean variable stepComplete sets to true, and the for loop breaks.
			* When step complete is true, the remaining for loop can not be entered.
			* The while loop then checks if there is more than one item remaining in the array, and sets the stepComplete bool to false once again.
			*/
			while (processedEquation.size() != 1) {
				stepComplete = false;
				
				for (int j = 0; j < processedEquation.size(); j++) {
					if (processedEquation.get(j).equals("*") || processedEquation.get(j).equals("/")) {
						float operand1 = Float.parseFloat(processedEquation.get(j-1));
						float operand2 = Float.parseFloat(processedEquation.get(j+1));
						float calcResult;
						if (processedEquation.get(j).equals("*"))
							calcResult = operand1*operand2;
						else
							calcResult = operand1/operand2;
						processedEquation.set(j, Float.toString(calcResult));
						processedEquation.remove(j+1);
						processedEquation.remove(j-1);
						stepComplete = true;
						break;
					}
				}
				if (!stepComplete) {
					for (int j = 0; j < processedEquation.size(); j++) {
						if (processedEquation.get(j).equals("+") || processedEquation.get(j).equals("-")) {
							float operand1 = Float.parseFloat(processedEquation.get(j-1));
							float operand2 = Float.parseFloat(processedEquation.get(j+1));
							float calcResult;
							if (processedEquation.get(j).equals("+"))
								calcResult = operand1+operand2;
							else
								calcResult = operand1-operand2;
							processedEquation.set(j, Float.toString(calcResult));
							processedEquation.remove(j+1);
							processedEquation.remove(j-1);
							stepComplete = true;
							break;
						}
					}
				}
			}
			//Once the while loop is complete, the only remaining item in the array list is the result
			equationResponse = "Expression Result: " + processedEquation.get(0);
		}
		return equationResponse;
	}
	
	/*
	* Method for prime factorization function of the server
	*/
	public String computeFactor(String input) {
		String factorResponse = "";
		int factorInput;
		int inputValue;
		ArrayList<Integer> factors = new ArrayList<Integer>();
		//Trys to convert input to integer, catches if input cannot be converted
		try {
			inputValue = Integer.parseInt(input);
			factorInput = Integer.parseInt(input);
		}
		catch (NumberFormatException e) { 
			return "Invalid Input";
		}
		
		if (inputValue < 2)
			return "Invalid Input"; 
		
		/*
		* Checks if the value to be factored exists in the factorList
 		* Key of the hashmap is the number to be factored, and value is the result of factorization
		* If key value pair exists in hashmap, return value of that key
		* If key exists, but value does not, print error to server, and continue
		*/
		if (factorList.containsKey(factorInput)) {
			if (factorList.get(factorInput) != null) {
				System.out.println("Using factor map");
				return ("Prime Factorization: " + factorList.get(factorInput));
			}
			else
				System.out.println("Factor Map error");
		}
		
		/*
		* Variable i represents the current prime factor being checked
		* Loops through each factor, until the current factor is larger than the number
		* While loops as long as the current number can be evenly divided by the current factor
		* If the number can be evenly divided, add the factor the the ArrayList factors, and divide the number by that factor.
		* If the number (inputValue) is non-zero after the for loop, the number itself is that last remaining prime factor.
		*/ 
		for (int i = 2; i <= inputValue; i++) {
			while((inputValue % i) == 0) {
				factors.add(i);
				inputValue = inputValue / i;
			}
		}
		if (inputValue>1)
			factors.add(inputValue);
		
		Collections.sort(factors); //Sorts the list of factors
		int currentFactor;
		int prevFactor = factors.get(0); //Skip the first item in the factors array, so prevFactor is set to item 0
		int counter = 1;
		
		//Goes through each item in the ArrayList of factors
		for (int i = 1; i < factors.size(); i++) {
			currentFactor = factors.get(i);
			//Since array is sorted, if the 2 factors are the same, increment counter
			if (prevFactor == currentFactor)
				counter++; 
			//Once the prevFactor and currentFactor are different (i.e. value of prevFactor has been counter)
			else {
				//Formats the just counted factor, and adds to the factorResponse string
				if (counter == 1)
					factorResponse = factorResponse.concat(Integer.toString(prevFactor) + " * ");
				else if (counter > 1)
					factorResponse = factorResponse.concat(Integer.toString(prevFactor) + "^" + Integer.toString(counter) + " * ");
				counter = 1;
			}
			prevFactor = currentFactor;
		}
		//Runs the formatting section of previous for loop again, as it misses the last factor.
		if (counter == 1)
			factorResponse = factorResponse.concat(Integer.toString(prevFactor) + " ");
		else if (counter > 1)
			factorResponse = factorResponse.concat(Integer.toString(prevFactor) + "^" + Integer.toString(counter) + " ");

		factorList.put(factorInput, factorResponse);
		return ("Prime Factorization: " + factorResponse);
	}
	
	/*
	* Method to check if a character is within a character array
	* Used in the computeEquation method
	*/
	public boolean contains(char c, char[] array) {
		for (char x : array) {
			if (x == c)
				return true;
		}
		return false;
	}
	
	/*
	* Method to check if the client should be timed out
	*/
	public boolean checkElapsedTime() {
		long endTime = lastInputTime + (long)(THREAD_DURATION*1000); //Calcualtes end time
		if (System.currentTimeMillis() >= endTime) {
			System.out.println("Client Number: " + clientNo + ", timeout");
			return false;
		}
		else 
			return true;
	}
}
