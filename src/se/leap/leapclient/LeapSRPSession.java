package se.leap.leapclient;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import org.jboss.security.Util;
import org.jboss.security.srp.SRPClientSession;
import org.jboss.security.srp.SRPParameters;
import org.jboss.security.srp.SRPPermission;

public class LeapSRPSession {

	private SRPParameters params;
	private BigInteger N;
	private BigInteger g;
	private BigInteger x;
	private BigInteger v;
	private BigInteger a;
	private BigInteger A;
	private byte[] K;
	/** The M1 = H(H(N) xor H(g) | H(U) | s | A | B | K) hash */
	private MessageDigest clientHash;
	/** The M2 = H(A | M | K) hash */
	private MessageDigest serverHash;

	private static int A_LEN;

	/** Creates a new SRP server session object from the username, password
	    verifier,
	    @param username, the user ID
	    @param password, the user clear text password
	    @param params, the SRP parameters for the session
	 */
	public LeapSRPSession(String username, char[] password, SRPParameters params)
	{
		this(username, password, params, null);
	}

	/** Creates a new SRP server session object from the username, password
	    verifier,
	    @param username, the user ID
	    @param password, the user clear text password
	    @param params, the SRP parameters for the session
	    @param abytes, the random exponent used in the A public key. This must be
	      8 bytes in length.
	 */
	public LeapSRPSession(String username, char[] password, SRPParameters params,
			byte[] abytes) {
		try {
			// Initialize the secure random number and message digests
			Util.init();
		}
		catch(NoSuchAlgorithmException e) {
		}

		this.params = params;
		this.g = new BigInteger(1, params.g);
		byte[] N_bytes = Util.trim(params.N);
		this.N = new BigInteger(1, N_bytes);

		if( abytes != null ) {
			A_LEN = 8*abytes.length;
			/* TODO Why did they put this condition?
	         if( 8*abytes.length != A_LEN )
	            throw new IllegalArgumentException("The abytes param must be "
	               +(A_LEN/8)+" in length, abytes.length="+abytes.length);
			 */
			this.a = new BigInteger(abytes);
		}

		// Calculate x = H(s | H(U | ':' | password))
		byte[] salt_bytes = Util.trim(params.s);
		byte[] xb = calculatePasswordHash(username, password, salt_bytes);
		this.x = new BigInteger(1, xb);

		// Calculate v = kg^x mod N
		String k_string = "bf66c44a428916cad64aa7c679f3fd897ad4c375e9bbb4cbf2f5de241d618ef0";
		this.v = calculateV(k_string);
		//String v_string = v.toString(16);

		serverHash = newDigest();
		clientHash = newDigest();

		// H(N)
		byte[] digest_of_n = newDigest().digest(N_bytes);
		
		// H(g)
		byte[] digest_of_g = newDigest().digest(params.g);
		
		// clientHash = H(N) xor H(g)
		byte[] xor_digest = xor(digest_of_n, digest_of_g, digest_of_g.length);
		//String hxg_string = new BigInteger(1, xor_digest).toString(16);
		clientHash.update(xor_digest);
		
		// clientHash = H(N) xor H(g) | H(U)
		byte[] username_digest = newDigest().digest(Util.trim(username.getBytes()));
		username_digest = Util.trim(username_digest);
		//String username_digest_string = new BigInteger(1, username_digest).toString(16);
		clientHash.update(username_digest);
		
		// clientHash = H(N) xor H(g) | H(U) | s
		//String salt_string = new BigInteger(1, salt_bytes).toString(16);
		clientHash.update(salt_bytes);
		
		K = null;
	}

	/**
	 * Calculates the parameter x of the SRP-6a algorithm.
	 * @param username
	 * @param password
	 * @param salt the salt of the user
	 * @return x
	 */
	public byte[] calculatePasswordHash(String username, char[] password, byte[] salt)
	{
		// Calculate x = H(s | H(U | ':' | password))
		MessageDigest x_digest = newDigest();
		
		// Try to convert the username to a byte[] using UTF-8
		byte[] user = null;
		byte[] colon = {};
		try {
			user = Util.trim(username.getBytes("UTF-8"));
			colon = Util.trim(":".getBytes("UTF-8"));
		}
		catch(UnsupportedEncodingException e) {
			// Use the default platform encoding
			user = Util.trim(username.getBytes());
			colon = Util.trim(":".getBytes());
		}

		byte[] passBytes = new byte[2*password.length];
		int passBytesLength = 0;
		for(int p = 0; p < password.length; p++) {
			int c = (password[p] & 0x00FFFF);
			// The low byte of the char
			byte b0 = (byte) (c & 0x0000FF);
			// The high byte of the char
			byte b1 = (byte) ((c & 0x00FF00) >> 8);
			passBytes[passBytesLength ++] = b0;
			// Only encode the high byte if c is a multi-byte char
			if( c > 255 )
				passBytes[passBytesLength ++] = b1;
		}
	
		// Build the hash
		x_digest.update(user);
		x_digest.update(colon);
		x_digest.update(passBytes, 0, passBytesLength);
		byte[] h = x_digest.digest();
		//h = Util.trim(h);
		
		x_digest.reset();
		x_digest.update(salt);
		x_digest.update(h);
		byte[] x_digest_bytes = Util.trim(x_digest.digest());

		return x_digest_bytes;
	}

	/**
	 * Calculates the parameter V of the SRP-6a algorithm.
	 * @param k_string constant k predefined by the SRP server implementation.
	 * @return the value of V
	 */
	private BigInteger calculateV(String k_string) {
		BigInteger k = new BigInteger(k_string, 16);
		return k.multiply(g.modPow(x, N));  // g^x % N
	}

	public byte[] xor(byte[] b1, byte[] b2, int length)
	{
		//TODO Check if length matters in the order, when b2 is smaller than b1 or viceversa
		byte[] xor_digest = new BigInteger(1, b1).xor(new BigInteger(1, b2)).toByteArray();
		return Util.trim(xor_digest);
	}

	/**
	 * @returns The exponential residue (parameter A) to be sent to the server.
	 */
	public byte[] exponential() {
		byte[] Abytes = null;
		if(A == null) {
			/* If the random component of A has not been specified use a random
	         number */
			if( a == null ) {
				BigInteger one = BigInteger.ONE;
				do {
					a = new BigInteger(A_LEN, Util.getPRNG());
				} while(a.compareTo(one) <= 0);
			}
			A = g.modPow(a, N);
			Abytes = Util.trim(A.toByteArray());
			//String Abytes_string = new BigInteger(1, Abytes).toString(16);

			// clientHash = H(N) xor H(g) | H(U) | A
			clientHash.update(Abytes);
			
			// serverHash = A
			serverHash.update(Abytes);
		}
		return Abytes;
	}

	/**
	 * Calculates the parameter M1, to be sent to the SRP server.
	 * It also updates hashes of client and server for further calculations in other methods.
	 * @param Bbytes the parameter received from the server, in bytes
	 * @return the parameter M1
	 * @throws NoSuchAlgorithmException
	 */
	public byte[] response(byte[] Bbytes) throws NoSuchAlgorithmException {
		// clientHash = H(N) xor H(g) | H(U) | s | A | B
		Bbytes = Util.trim(Bbytes);
		//String Bbytes_string = new BigInteger(1, Bbytes).toString(16);
		clientHash.update(Bbytes);
		
		// Calculate S = (B - kg^x) ^ (a + u * x) % N
		BigInteger S = calculateS(Bbytes);
		byte[] S_bytes = Util.trim(S.toByteArray());
		//String S_bytes_string = new BigInteger(1, S_bytes).toString(16);

		// K = SessionHash(S)
		String hash_algorithm = params.hashAlgorithm;
		MessageDigest sessionDigest = MessageDigest.getInstance(hash_algorithm);
		K = sessionDigest.digest(S_bytes);
		//K = Util.trim(K);
		//String K_bytes_string = new BigInteger(1, K).toString(16);
		
		// clientHash = H(N) xor H(g) | H(U) | A | B | K
		clientHash.update(K);
		byte[] M1 = clientHash.digest();
		
		// serverHash = Astr + M + K
		serverHash.update(M1);
		serverHash.update(K);
		return M1;
	}

	/**
	 * It calculates the parameter S used by response() to obtain session hash K.
	 * @param Bbytes the parameter received from the server, in bytes
	 * @return the parameter S
	 */
	private BigInteger calculateS(byte[] Bbytes) {
		byte[] Abytes = Util.trim(A.toByteArray());
		byte[] u_bytes = getU(Abytes, Bbytes);
		//ub = Util.trim(ub);
		
		BigInteger B = new BigInteger(1, Bbytes);
		BigInteger u = new BigInteger(1, u_bytes);
		//String u_string = u.toString(16);
		
		BigInteger B_minus_v = B.subtract(v);
		BigInteger a_ux = a.add(u.multiply(x));
		//String a_ux_string = a_ux.toString(16);
		BigInteger S = B_minus_v.modPow(a_ux, N);

		return S;
	}

	/**
	 * It calculates the parameter u used by calculateS to obtain S.
	 * @param Abytes the exponential residue sent to the server
	 * @param Bbytes the parameter received from the server, in bytes
	 * @return
	 */
	public byte[] getU(byte[] Abytes, byte[] Bbytes) {
		MessageDigest u_digest = newDigest();
		u_digest.update(Abytes);
		u_digest.update(Bbytes);
		return new BigInteger(1, u_digest.digest()).toByteArray();
	}

	/**
	 * @param M2 The server's response to the client's challenge
	 * @returns True if and only if the server's response was correct.
	 */
	public boolean verify(byte[] M2)
	{
		// M2 = H(A | M1 | K)
		byte[] myM2 = serverHash.digest();
		boolean valid = Arrays.equals(M2, myM2);
		return valid;
	}

	/** Returns the negotiated session K, K = SHA_Interleave(S)
	    @return the private session K byte[]
	    @throws SecurityException - if the current thread does not have an
	    getSessionKey SRPPermission.
	 */
	public byte[] getSessionKey() throws SecurityException
	{
		return K;
	}

	
	/**
	 * @return a new SHA-256 digest.
	 */
	public MessageDigest newDigest()
	{
		MessageDigest md = null;
		try {
			md = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		return md;
	}
}