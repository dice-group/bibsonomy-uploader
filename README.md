# bibsonomy-uploader
Our simple Java command line tool for uploading bibtex files to bibsonomy

## Build
Its plain maven.

```bash
mvn clean install
```

## Usage

A bundled jar file is generated under `bibsonomy-uploader-debian-cli/target`, therefore, from the project root directory it can be run with:


```bash
java -cp `find bibsonomy-uploader-debian-cli/target -name 'bibsonomy-uploader*jar'` org.aksw.bibuploader.BibUpdater username apikey apiurl bibtex-file
```

## Running on Java 11+ (tested up to 22.0.2)

Since JAXB was removed from the JDK starting with Java 11, you need to add JAXB at runtime and disable JAXB's bytecode optimization. No code changes are required.

1. Add JAXB dependencies (download JARs into a local `lib/` folder)
  - `jakarta.xml.bind-api-2.3.3.jar`
  - `jaxb-runtime-2.3.3.jar` (from `org.glassfish.jaxb`)
  - `jakarta.activation-api-1.2.2.jar`
  > Ensure the `lib/` directory sits next to your runnable JAR (or adjust the path accordingly).
2. Run using classpath + main class (not `-jar`)
  ```bash
    java \
      --add-opens java.base/sun.misc=ALL-UNNAMED \
      -Dcom.sun.xml.bind.v2.bytecode.ClassTailor.noOptimize=true \
      -cp "<YOUR_JAR>:lib/*" \
      org.aksw.bibuploader.BibUpdater <username> <apikey> <apiurl> <bibtex-file>
  ```
  > Note: On Windows, use `;` insted of `:` in the `-cp` argument.

## Building the jar

mvn clean compile assembly:single

## Note, only executable with Java 1-8-101 or higher

java --add-modules java.xml.bind -jar target/bibsonomy-uploader-cli-0.9.0-SNAPSHOT-jar-with-dependencies.jar aksw "insertAPIkeyHERE" "http://www.bibsonomy.org/api" ~/Papers/bib/aksw.bib 

## Debian Package

### Removing and (re-)installing the Debian package

```
sudo apt-get purge aksw-bibsonomy-uploader

sudo dpkg -i `find bibsonomy-uploader-debian-cli/target -name '*.deb'`

# This command is now available:
aksw-bibsonomy-uploader
```

