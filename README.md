# alpakka-mqtt-streaming-example

This respository implements a simple mqtt client using the `mqtt.streaming`-backend from alpakka.

Tcp- and websocket-connections are both implemented.

## Start Mosquitto
Use the provided mosquitto config file in order to open a tcp listener
at port `1883`, a websocket listener at port `9001`, allow anonymous connections
and log (almost) everything.
```
mosquitto -c config/mosquitto.conf
```

## Publish to Mosquitto
After mosquitto has been started succesfully run the project

```
sbt run
```
