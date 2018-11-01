# comp512-project



For convenience when logging into the different machines in run_servers.sh, set up ssh keys on your account.
Do so with the command ```ssh-keygen```, then you won't have to enter your password on every machine when logging in.

To run the RMI resource manager:

```
cd Server/
./run_server.sh [<rmi_name>] # starts a single ResourceManager
./run_servers.sh # convenience script for starting multiple resource managers
```

To run the RMI client:

```
cd Client
./run_client.sh [<server_hostname> [<server_rmi_name>]]
```
