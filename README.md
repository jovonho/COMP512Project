# COMP512 Group Project


For convenience when logging into the different machines in run_servers.sh, set up ssh keys on your account.
Do so with the command ```ssh-keygen```, then you won't have to enter your password on every machine when logging in.

To run the RMI resource managers and Middleware:

```
cd Server/
./run_servers.sh # convenience script for starting multiple resource managers
```
This starts the resource managers on 3 fixed machines and the Middleware on localhost.


To run the RMI client:

```
cd Client
./run_client.sh localhost Middleware
```
