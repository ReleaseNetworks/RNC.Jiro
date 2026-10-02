# RNC.Jiro

RNC.Jiro is a system based on the Heltec LoRa 32 Platform that allows us to remote control and collect telemetry data from remote devices like vehicles using LoRa WAN. 

Using the protocol and its software with the hardware custom profiles can be created to collect specific data in realtime which is streamed back to the receiver.

## How it works

The system needs at least one transceiver and one receiver and ofc two antennas. Multiple transceivers can be combined to create a mesh-network to submit data across multiple hops. Ofc multiple transceivers can be connected into the established network if multiple devices need to process or emit data from the network.

The hardware receiver can be connected to a computer via a USB-C cable. Applications can receive data submitted to the receiver via a serial connection. This repository also provides programmatic approaches for specialised use cases.

## Motivation

The primary use case for our team is to use these devices to monitor several of our miniature autonomous vehicles and collect decision and telemetry data, such as battery charge, temperature, velocity, collision sensors and much more.



## How to use

``` java
IJiro jiroDevice = new Jiro("COM3", 115200); //custom baud. lower if running into problems

//register a field in the com channel
device.registerField("battery", Protocol.TYPE_FLOAT, Protocol.ROLE_INPUT);

//subscribe to updates from the remote component
jiroDevice.onUpdate("battery", value -> System.out.printf("battery = %.2f V%n", ((Number) v).floatValue()));

jiroDevice.start();

Thread.sleep(5000);

jiroDevice.showText(some string); //will be shown on remote display if one is available. weve been using that to identify which board is which and to detect sync offsites

jiroDevice.showFields(some registered fields); // shows the values of the registered fields on remote display if available

```

## Debugging

If you encdounter any issues connecting devices you might wanna run the debugging scripts inside the `debugging` folder. It is an powershell script which prints out any data received from the selected serial port. since it has been developed on windows a powershell script might come in handy and i also know some ppl that really love powershell...for some reasons idk why 