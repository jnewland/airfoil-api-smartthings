# Control Airfoil with SmartThings

[![No Maintenance Intended](http://unmaintained.tech/badge.svg)](http://unmaintained.tech/)

Uses [airfoil-api](https://github.com/jnewland/airfoil-api) to control Rogue
Amoeba's [Airfoil](https://www.rogueamoeba.com/airfoil/) with
[SmartThings](http://www.smartthings.com/).

![](http://virtual-host-discourse.global.ssl.fastly.net/uploads/smartthings/optimized/3X/b/e/beab2c7c205b50784a50ba7430a278b1d9830544_1_281x499.jpg)

![](http://virtual-host-discourse.global.ssl.fastly.net/uploads/smartthings/optimized/3X/7/6/76c7157fdf233ef86aa8c611ca30cb1fa4db891d_1_281x499.jpg)

This project essentially duplicates the functionality of
[Airfoil Remote](https://www.rogueamoeba.com/airfoil/remote/) in a way that you
can integrate with the rest of your SmartThings automation.

## Crazy things you could do with this

* Mount an iPad on the wall for easy volume control
* Control Airfoil speaker volume with a Z-Wave dimmer
* Turn on and off Airfoil speakers
  [with HomeKit and Siri](http://community.smartthings.com/t/hello-home-homekit-and-siri-control-via-homebridge/16701)

### Setup

#### Airfoil & API

* Start Airfoil
* Setup [airfoil-api](https://github.com/jnewland/airfoil-api) on the computer
  running Airfoil

#### SmartThings web setup

* Install [airfoil-speaker.groovy](airfoil-speaker.groovy) as a device handler
* Install [airfoil-api-connect.groovy](airfoil-api-connect.groovy) as a smart app

#### SmartThings phone setup

* Tap the `+` icon in your SmartThings iOS app.
* Navigate from the "Things" tab to the "My Apps" tab
* Tap "Airfoil API Connect"
* Enter the IP, port (default `8080`) and name of the computer running Airfoil
  * TODO: get dns-sd working to remove this step
* Select the speakers you'd like to control
* Tap "Done" and navigate back to the SmartThings home screen.
* Tap the `+` icon in your SmartThings iOS app and add the detected speakers
  to your device list.

# Disclaimer

SmartThings, Airfoil, and Z-Wave are all trademarks of their respective owners.
