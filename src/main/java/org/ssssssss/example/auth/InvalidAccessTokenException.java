package org.ssssssss.example.auth;

public class InvalidAccessTokenException extends RuntimeException {

	public InvalidAccessTokenException() {
		super("Invalid access credentials or token");
	}
}
