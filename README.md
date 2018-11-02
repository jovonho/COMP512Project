# COMP512 Group Project


For convenience when logging into the different machines in run_servers.sh, set up ssh keys on your account.
Do so with the command ```ssh-keygen```, then you won't have to enter your password on every machine when logging in.

Run ```make``` in both ```Server/``` and ```Client/``` directories before anything if this is your first time running.

To run the RMI resource managers and Middleware:

```
cd Server/
./run_servers.sh # convenience script for starting multiple resource managers
```
This will use the terminal multiplexer ```tmux``` to ssh into 3 serparate machines to run the resource managers and start the Middleware on localhost.
To switch between panes use ```Ctrl + b``` (don't hold it just press it once) followed by the arrow-key in the direction you want to move.
To close the tmux session use ```tmux ls``` (in any pane) to get the current session number, then ```tmux kill-session -t [session number]```. Usually, the session number is 0.


To run the RMI client:

```
cd Client
./run_client.sh localhost Middleware
```
