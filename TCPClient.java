import java.net.*;
import java.io.*;

public class TCPClient {
	public static void main(String[] args) throws Exception {
		try {
			Socket client = new Socket("127.0.0.1",7896);
			
			//DataInputStream i = new DataInputStream(client.getInputStream());
			BufferedReader r = new BufferedReader(new InputStreamReader(client.getInputStream()));
			PrintStream w = new PrintStream(client.getOutputStream());
			BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
			
			String userInput, serverResponse;
			
			//Initial client connection instructions.
			System.out.println("\nEnter 'equation' to calculate simple math equations.");
			System.out.println("  - Equations must be in format 'X o X o X', where X is a number, and o is an operator");
			System.out.println("  - Valid operators are: *, /, +, -");
			System.out.println("Enter 'factor' to factor a prime number.");
			System.out.println("  - Enter any integer number, greater than 1");
			System.out.println("  - Result will be in the format of 'X^X * X^X ...'\n");
			System.out.println("After 10 minutes of inactivity, you will be timed-out.\n");
			System.out.println("Type 'exit' to close client connection.");
			System.out.println("Type 'shutdown' to close server.\n");
			
			
			
			while ((userInput = in.readLine()) != null) {
				w.println(userInput);
				w.flush();
				if (userInput.equals("exit") || userInput.equals("shutdown"))
					break;
				serverResponse = r.readLine();
				if (serverResponse == null) {
					System.out.println("Disconnected from server");
					break;
				}
				System.out.println(serverResponse);
			}
			w.close();
			client.close();
			
		} catch (UnknownHostException e) {
			System.out.println("Sock: " + e.getMessage()); 
		} catch (EOFException e) {
			System.out.println("EOF: " + e.getMessage());
		} catch (IOException e) {
			System.out.println("IO: " + e.getMessage());
		}
	}
}

