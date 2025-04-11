# Networked Producer and Consumer

Producer-consumer exercise involving file writes and network sockets. This includes concurrent programming, file I/O, queueing, and network communication. It simulates a simulation of a media upload service. Both the producer and consumer runs on a different virtual machine and communicate with each other via the network.

```
The program accepts inputs from the user.

p - Number of producer threads/instances. Each thread must read from a separate folder containing the videos.

c - Number of consumer threads

q - Max queue length. For simplicity we will use a leaky bucket design, additional videos beyond the queue capacity will be dropped.
```

## Producer

Reads the a video file, uploads it to the media upload service (consumer). Before running the program, make sure to update the IP address to point to your Consumer virtual machine.

```
javac Producer.java
java Producer
```

## Consumer

Accepts simultaneous media uploads. Saves the uploaded videos to a single folder. Displays and previews the 10 seconds of the uploaded video file.

```
javac Consumer.java
java Consumer
```
