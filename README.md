# Message System
A basic message system for a university project (COMP3331) with the ability to handle several clients simultaneously.

The message system designed implements 1 server in which several clients are able to log in and issue commands allowing for client interaction. Both the server and client program use multi-threading to both allow the server to handle multiple concurrent clients, client communication with the server and also to allow for peer to peer messaging of clients.

The system was written in Java and in the design, uses 3 main classes for most of the operations; the Server, Client and User class. While the server and client class are mostly self-explanatory with their name, the User class is what allows the server to handle the client’s commands. The User object contains all the information about a certain user that is registered through the credentials.txt file. This includes their username, password, online status, blocked users etc. which is referred to by the server to process client requests. There are other classes involved in operation including 2 classes for peer to peer messaging however it is these 3 that form the foundation for the message system.

For more details, please read the report.pdf
