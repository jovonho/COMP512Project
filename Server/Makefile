all: java.policy RMIInterface.jar RMItem.jar compile-server-rmi compile-middleware

java.policy: FORCE
	@echo "Creating server java policy"
	@echo "grant codebase \"file:`pwd`/\" {" > java.policy
	@echo "permission java.security.AllPermission;" >> java.policy
	@echo "};" >> java.policy

FORCE: ;

RMIInterface.jar: Server/Interface/IResourceManager.java 
	@echo "Compiling RMI server interface"
	javac Server/Interface/*.java
	jar cvf RMIInterface.jar Server/Interface/*.class

RMItem.jar: Server/Common/RMItem.java
	@echo "Compiling RMItem jar"
	javac Server/Common/RMItem.java
	jar cvf RMItem.jar Server/Common/RMItem.class

compile-server-rmi: Server/RMI/*.java Server/Interface/IResourceManager.java Server/Common/*.java
	javac Server/RMI/*.java Server/Interface/IResourceManager.java Server/Common/*.java

compile-middleware: Server/RMI/*.java Server/Interface/IResourceManager.java Server/Middleware/*.java Server/Common/*.java
	@echo "Compiling Middleware"
	javac Server/RMI/*.java	Server/Interface/IResourceManager.java Server/Middleware/*.java Server/Common/*.java

clean:
	rm -f Server/Interface/*.class Server/Common/*.class Server/RMI/*.class
	rm -f RMIInterface.jar
	rm -f java.policy 
