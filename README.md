### Gobannos
Android application to control smartphone sensors

## How to set up your phone as a remote multi-sensor
# Install Gobannos
Go to [Gobannos Page](https://github.com/Turbotice/gobannos/tree/main/gobannos) and download Gobannos.apk
Install it, and allow appropriate authorizations during the process

# Run Gobannos
Start Gobannos application.
On the phone, switch to a web browser and type <http://127.0.0.1:8080/status> to check phone status. If you get a response, the link is active. You can manipulate Gobannos state using basic commands :
/start
/stop

On a local network, note the IP displayed by Gobannos and type <http://phone_ip:8080/status> in a web browser

On different local network, you can use the private network Turbotice set up in Zerotier. Contact for more information.

#Control using Phonefleet
Download Phonefleet at <https://github.com/Turbotice/phonefleet> and follow instructions