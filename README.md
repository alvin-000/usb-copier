# usb-copier
Fork of [lukehutch/usb-copier](https://github.com/lukehutch/usb-copier) and [2wenty2wo/usb-copier](https://github.com/2wenty2wo/usb-copier) designed to make turn the original example project into a more practical appliance to copy files from a volume, clone boot disks, check files on a disk before putting in Windows computer, or re-format/wipe disk. 

![image](https://github.com/alvin-000/usb-copier/assets/152043358/ab89898e-d857-4dea-95d7-d55ab86e829e)


## Functionality Description
- **Copy:** Copies all files from one volume onto another disk's volume. THe destination partition is not altered. Destination volume must be larger than total size of all files
- **DD:** DD copied the entire disk from selected disk to destination disk. Destination disk must be larger than selected disk
- **View:** View a list of all files on a volume
- **Wipe Quick:** Format entire selected disk to FAT32
- **Wipe Secure:** One pass of 00 accross the selected entire disk and format to FAT32

## Fork Change Overview
### Additional Functionality
1. Added DD Copy funtionaity and menu, to include progress bar of copy/wipe
2. Set Wipe-> Quick to format entire disk (was only partition before)
3. Set Wipe-> Secure to DD Zero entire disk (was only partition before)
4. Show true size of disk in parenthesis (in events where there is a multi-partition/volume disk)
5. DD commands use 64K for `bs` for faster clone/wipe

### Known Issues
1. Drive formatting is done directly and without adding patrition table (not all devices will support reading this)
2. Setup instructions do not work on latest Raspian distro (Bookwork), but still work on Raspian Buster. This means it is not compatible with Raspberry Pi 5 (Bookworm or above). As this is a legacy OS, it is not recommened this device be Internet connected. 
3. ExFat automount is not supported at this time
4. No unmount function (FAT32 volume files are unlikely to be damaged, but the safety bit will just not be written to disk)
5. Disk will not show if it has no mounted volumes

## Required Items
- Raspberry Pi 4 or below (Pi 5 is NOT compatible with Raspian Buster)
  1. Pi Zero is compatible with USB HAT or USB extension
  2. Using a Pi 4 will be fastest as it has USB 3.0 ports
- [Adafruit Bonnet OLED for Pi](https://www.adafruit.com/product/3531)
- microSD for Raspian OS (8GB+)
- Power Supply for your Pi

## Setup instructions

### Using Provided Image (FUTURE RELEASE)
Use DD, Rufus, BelenaEtcher, or another image program to write .img file to microSD card.

### Using Provided Driver and Java File
Instructions from: https://shaunjay.com/2021/03/28/raspberry-pi-zero-usb-copier/
1. Install [Raspberry Pi OS Lite](https://downloads.raspberrypi.org/raspios_oldstable_lite_armhf/images/raspios_oldstable_lite_armhf-2023-05-03/) on Raspberry Pi Zero 1.3.
2. Install [Java 11 for ARMv6 provided by Azul](https://webtechie.be/post/2020-08-27-azul-zulu-java-11-and-gluon-javafx-11-on-armv6-raspberry-pi/) as openjdk-11-jdk won’t run on the Zero.
3. Install `sudo apt-get install wiringpi pigpio udevil nano`
4. Run `sudo nano /boot/cmdline.txt` and add `iomem=relaxed`
5. Enable I2C by running `sudo raspi-config` and turn it on under `Interfaces`
6. Set I2C data rate in config file. `sudo nano /boot/config.txt` & add `,i2c_arm_baudrate=1000000` after `dtparam=i2c_arm=on`
7. Extract libpi4j-pigpio.so to /home/pi: unzip -j usb-copier-0.0.2-jar-with-dependencies.jar lib/armhf/libpi4j-pigpio.so
8. Start at boot: `sudo nano /etc/rc.local` and add `sudo bash -c 'nohup java -Dpi4j.library.path=/home/pi -jar /home/pi/usb-copier-0.0.2-jar-with-dependencies.jar &'` (or whatever you named the files)

### Build yourself
On Linux build machine:

* `git clone https://github.com/lukehutch/Adafruit-OLED-Bonnet-Toolkit.git`
* `cd Adafruit-OLED-Bonnet-Toolkit ; mvn install ; cd ..`
* `git clone https://github.com/lukehutch/usb-copier.git`
* `cd usb-copier ; mvn package ; cd ..`
* Then copy `usb-copier/target/usb-copier-0.0.2-jar-with-dependencies.jar` to `/home/pi` on the Raspberry Pi.

