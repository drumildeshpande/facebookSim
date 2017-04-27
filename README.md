# **Facebook Simulator**


This projects aims at creating a Distributed system to simulate facebook functionalities using scala , akka and spray-can. It contains implementation of REST APIs such as Friend List, Profile, Page, Photos, Albums. The code for the facebook simulaltor can be found in the fbSim dir and the code for the fb simulator with end-to-end encryption (using SHA-256,AES-256 and RSA-2048 algorithms to secure data) can be found in the fbSimSecure directory.

To run fbSim project:

	Step1 - Go to Project-4-1 > Server

	Step2 - Type the following command to start the facebook server:
		sbt run

	Step3 - Go to Project-4-1 > Client

	Step4 - Type the following command to initiate the clients which will create the facebook users:
		sbt "run client <number of clients>"

Here the <number of clients> parameter is an Interger.
In order to terminate the program the client shuts down after some time.
The output of every request can be seen on the server and client side.

To run fbSimSecure:

	1. First start the server (Run server.scala file)
	2. Start the client (Run the client.scala file)
The client file takes input as : "client <number of users>" (without quotes)
<number of users> can be any Integer

