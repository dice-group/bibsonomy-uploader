# bibsonomy-uploader
Our simple Java command line tool for uploading bibtex files to bibsonomy

## Building the jar

You'll need a JDK 11 or later to build the application.  Use the following command:

```bash
mvn clean compile assembly:single
```


## Running 

```
java --add-opens java.base/sun.misc=ALL-UNNAMED -jar <jar_file.jar> <username> <apikey> <apiurl> <bibtex-file>
```


## Debian Package

## Usage

A bundled jar file is generated under `bibsonomy-uploader-debian-cli/target`, therefore, from the project root directory it can be run with:


```bash
java -cp `find bibsonomy-uploader-debian-cli/target -name 'bibsonomy-uploader*jar'` org.aksw.bibuploader.BibUpdater username apikey apiurl bibtex-file
```

### Removing and (re-)installing the Debian package

```
sudo apt-get purge aksw-bibsonomy-uploader

sudo dpkg -i `find bibsonomy-uploader-debian-cli/target -name '*.deb'`

# This command is now available:
aksw-bibsonomy-uploader
```

