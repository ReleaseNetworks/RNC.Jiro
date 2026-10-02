# RNC.Jiro

RNC.Jiro is a system based on the Heltec LoRa 32 Platform that allows us to remote control and collect telemetry data from remote devices like vehicles using LoRa WAN. 

Using the protocol and its software with the hardware custom profiles can be created to collect specific data in realtime which is streamed back to the receiver.

## How it works

The system needs at least one transceiver and one receiver and ofc two antennas. Multiple transceivers can be combined to create a mesh-network to submit data across multiple hops. Ofc multiple transceivers can be connected into the established network if multiple devices need to process or emit data from the network.

The hardware receiver can be connected to a computer via a USB-C cable. Applications can receive data submitted to the receiver via a serial connection. This repository also provides programmatic approaches for specialised use cases.

## Motivation

The primary use case for our team is to use these devices to monitor several of our miniature autonomous vehicles and collect decision and telemetry data, such as battery charge, temperature, velocity, collision sensors and much more.
