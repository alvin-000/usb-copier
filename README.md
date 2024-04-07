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
2. Secure Wipe may not format disk after successful dd wipe is complete due to dd error reporting from items like byte size mismatch or block mis-allignment. 
3. ExFat automount is not supported at this time
4. No unmount function (FAT32 volume files are unlikely to be damaged as long as it is not being written to when removed, but the safety bit will just not be written to disk)
5. Disk will not show if it has no mounted volumes

## Required Items
- Raspberry Pi 4 or below (Pi 5 is NOT compatible at this time due to changes in GPIO)
  1. Pi Zero is compatible with USB HAT or USB extension
  2. Using a Pi 4 will be fastest as it has USB 3.0 ports
- [Adafruit Bonnet OLED for Pi](https://www.adafruit.com/product/3531)
- microSD for Raspian OS (8GB+)
- Power Supply for your Pi

## Setup instructions

### Using Provided Image
Unzip file with tools like 7-Zip, Zip, or unzip. Then use DD, Rufus, BelenaEtcher, or another image program to write .img file to microSD card.

### Using Provided Driver and Java File
1. Install latest version of [Raspian 32-bit](https://www.raspberrypi.com/software/operating-systems/)
2. Update source list `sudo apt-get update`
3. Install dependencies `sudo apt install default-jdk udevil`
4. Install WiringPi manuall, since it does not appear avaliable via apt any more
````
wget https://github.com/WiringPi/WiringPi/releases/download/3.2/wiringpi_3.2_armhf.deb
sudo dpkg -i wiringpi_3.2_armhf.deb
````
5. Run `sudo nano /boot/firmware/cmdline.exe` and add `iomem=relaxed`
6. Enable I2C by running `sudo raspi-config` and turn it on from Interfaces.
7. Set I2C data rate in config file. `sudo nano /boot/firmware/config.txt` & add `,i2c_arm_baudrate=1000000` after `dtparam=i2c_arm=on`
8. Move files to `/home/pi` (your user) directory
9. (Optional, if downloaded) Extract libpi4j-pigpio.so to /home/pi: unzip -j usb-copier-0.0.2-jar-with-dependencies.jar lib/armhf/libpi4j-pigpio.so
10. Set program to start at boot: `sudo nano /etc/rc.local` and add `sudo bash -c 'nohup java -Dpi4j.library.path=/home/pi -jar /home/pi/usb-copier-0.0.2-jar-with-dependencies.jar &'` (files name and directory path may differ based on your selectednames)

### Build yourself
On Linux build machine:

* `git clone https://github.com/lukehutch/Adafruit-OLED-Bonnet-Toolkit.git`
* `cd Adafruit-OLED-Bonnet-Toolkit ; mvn install ; cd ..`
* `git clone https://github.com/lukehutch/usb-copier.git`
* `cd usb-copier ; mvn package ; cd ..`
* Then copy `usb-copier/target/usb-copier-0.0.2-jar-with-dependencies.jar` to `/home/pi` on the Raspberry Pi.

