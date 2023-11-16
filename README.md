# Lightstreamer - Chat Demo - Spring Boot Adapter #

The *Lightstreamer Basic Chat Demo* is a very simple chat application, based on [Lightstreamer](http://www.lightstreamer.com) for its real-time communication needs.
This project contains the source code and all the resources needed to develop remote adapters for the demo based on a [Spring Boot stand-alone apllication](https://spring.io/projects/spring-boot#overview).
Developing remote adapters for Lightstreamer-based applications using Spring Boot can benefit from a simplified setup, efficient integration, and robust testing capabilities.

As an example of a client using this adapter, you may refer to the [Basic Chat Demo - HTML Client](https://github.com/Lightstreamer/Lightstreamer-example-chat-client-javascript) and view the corresponding [Live Demo](http://demos.lightstreamer.com/ChatDemo/).

## Details

This project demonstrates the implementation of the DataProvider and MetadataProvider interfaces of the [Lightstreamer Java Remote Adapter API](https://www.lightstreamer.com/api/ls-adapter-remote/latest/) to implement the features of the chat demo.

### Dig the Code

The project consists of the following Java source files:
 - `ChatAdapterSpringbootApplication.java`, implements ApplicationRunner interface of the Spring framework. Spring’s @Value annotation is used to interpret the input parameters to use for configuring the adapters.
 - `ServerStarter.java`, utility class that allows to instantiate and launch a Data or Metadata adapter.
 - `ChatDataAdapter.java`, contains the source code for the Chat Data Adapter. The Data Adapter accepts message submission for the unique chat room. The sender is identified by an IP address and a nickname.
It's possible to flush chat history based on an optional parameter.
 - `ChatMetaDataAdapter.java`, contains the source code for a Metadata Adapter.
The Metadata Adapter inherits from the reusable [LiteralBasedProvider](https://sdk.lightstreamer.com/ls-adapter-remote/1.7.0/api/com/lightstreamer/adapters/remote/metadata/LiteralBasedProvider.html) and just adds a simple support for message submission. It should not be used as a reference for a real case of client-originated message handling, as no guaranteed delivery and no clustering support is shown.

#### The Adapter Set Configuration
This Adapter Set is configured and will be referenced by the clients as `CHAT_REMOTE`.
As *Proxy Data Adapter* and *Proxy MetaData Adapter*, you may configure also the robust versions.
The *Robust Proxy Data Adapter* and *Robust Proxy MetaData Adapter* have some recovery capabilities and avoid to terminate the Lightstreamer Server process, so it can handle the case in which a Remote Data Adapter is missing or fails, by suspending the data flow and trying to connect to a new Remote Data Adapter instance.
Full details on the recovery behavior of the Robust Data Adapter are available as inline comments within the [provided template](https://lightstreamer.com/docs/ls-ARI/latest/adapter_robust_conf_template/adapters.xml).

The `adapters.xml` file for this demo should look like:

```xml
<?xml version="1.0"?>

<adapters_conf id="CHAT_REMOTE">
    <metadata_adapter_initialised_first>N</metadata_adapter_initialised_first> 
    <metadata_provider>
        <adapter_class>ROBUST_PROXY_FOR_REMOTE_ADAPTER</adapter_class>
        <classloader>log-enabled</classloader>
        <param name="request_reply_port">27580</param>
        <param name="connection_recovery_timeout_millis">10000</param>
        <param name="first_connection_timeout_millis">10000</param>
        <param name="close_notifications_recovery">unneeded</param>
    </metadata_provider>


    <data_provider name="CHAT_DATA">
        <adapter_class>ROBUST_PROXY_FOR_REMOTE_ADAPTER</adapter_class>
        <classloader>log-enabled</classloader>
        <param name="request_reply_port">27780</param>
        <param name="connection_recovery_timeout_millis">10000</param>
        <param name="events_recovery">use_snapshot</param>
    </data_provider>

</adapters_conf>
```

<i>NOTE: not all configuration options of a Proxy Adapter are exposed by the file suggested above.
You can easily expand your configurations using the generic template
for [basic](https://lightstreamer.com/docs/ls-ARI/latest/adapter_conf_template/adapters.xml) and [robust](https://lightstreamer.com/docs/ls-ARI/latest/adapter_robust_conf_template/adapters.xml) Proxy Adapters as a reference.</i>

## Build and Run

If you want to build and run a version of this demo with your local Lightstreamer Server, follow these steps (the project requires [Apache Maven](https://maven.apache.org/) installed on your system):

 - Download *Lightstreamer Server* (Lightstreamer Server comes with a free non-expiring demo license for 20 connected users) from [Lightstreamer Download page](https://lightstreamer.com/download/), and install it, as explained in the `GETTING_STARTED.TXT` file in the installation home directory.
 - Create a folder called `RemoteChat` in the `LS_HOME/adapters` folder.
 - Create a file named `adapters.xml` in the `RemoteChat` folder, with the contents from the section above.
 - Launch Lightstreamer Server. The Server startup will complete only after a successful connection between the Proxy Data Adapter and the Remote Data Adapter.
 - In a terminal window, with the working directory set to 'chat-adapter-springboot', run the following command:
 ```sh 
 ./mvnw spring-boot:run "-Dspring-boot.run.arguments=--server.name=chat-spring"
 ```
 - Test the Adapter, launching the [Lightstreamer - Basic Chat Demo - HTML Client](https://github.com/Lightstreamer/Lightstreamer-example-Chat-client-javascript) listed in [Clients Using This Adapter](https://github.com/Lightstreamer/Lightstreamer-example-Chat-adapter-node#clients-using-this-adapter).
    - To make the [Lightstreamer - Basic Chat Demo - HTML Client](https://github.com/Lightstreamer/Lightstreamer-example-Chat-client-javascript) front-end pages get data from the newly installed Adapter Set, you need to modify the front-end pages and set the required Adapter Set name to PROXY_NODECHAT when creating the LightstreamerClient instance. So edit the `lsClient.js` file of the *Basic Chat Demo* front-end deployed under `Lightstreamer/pages/ChatDemo` and replace:<BR/>
`var lsClient = new LightstreamerClient(protocolToUse+"//localhost:"+portToUse,"CHAT");`<BR/>
with:<BR/>
`var lsClient = new LightstreamerClient(protocolToUse+"//localhost:"+portToUse,"CHAT_REMOTE");`<BR/>
(you don't need to reconfigure the Data Adapter name, as it is the same in both Adapter Sets).
    - As the referred Adapter Set has changed, make sure that the front-end no longer shares the Engine with other demos.
So a line like this:<BR/>
`lsClient.connectionSharing.enableSharing("ChatDemoCommonConnection", "ATTACH", "CREATE");`<BR/>
should become like this:<BR/>
`lsClient.connectionSharing.enableSharing("RemoteChatDemoConnection", "ATTACH", "CREATE");`
    - Open a browser window and go to: [http://localhost:8080/ChatDemo](http://localhost:8080/ChatDemo)

#### Add Encryption

This feature requires Server version 7.1.0 (which corresponded to Adapter Remoting Infrastructure, i.e. Proxy Adapters, 1.9.6) or newer.

Each TCP connection from a Remote Adapter can be encrypted via TLS. To have the Proxy Adapters accept only TLS connections, a suitable configuration should be added in adapters.xml in the <data_provider> block, like this:
```xml
  <data_provider>
    ...
    <param name="tls">Y</param>
    <param name="tls.keystore.type">JKS</param>
    <param name="tls.keystore.keystore_file">./myserver.keystore</param>
    <param name="tls.keystore.keystore_password.type">text</param>
    <param name="tls.keystore.keystore_password">xxxxxxxxxx</param>
    ...
  </data_provider>
```
and the same should be added in the <metadata_provider> block.

This requires that a suitable keystore with a valid certificate is provided. See the configuration details in the [provided template](https://lightstreamer.com/docs/ls-ARI/latest/adapter_robust_conf_template/adapters.xml).
NOTE: For your experiments, you can configure the adapters.xml to use the same JKS keystore "myserver.keystore" provided out of the box in the Lightstreamer distribution. Since this keystore contains an invalid certificate, remember to configure your local environment to "trust" it.
The provided source code is already predisposed for TLS connection on all ports. 
You can rerun the adapters with the new configuration by launching:<BR/>
 ```sh 
 ./mvnw spring-boot:run "-Dspring-boot.run.arguments=--server.tls=true --server.name=chat-spring"
 ```
where the same *my.host.name* supported by the provided certificate must be supplied.

#### Add Authentication

Each TCP connection from a Remote Adapter can be subject to Remote Adapter authentication through the submission of user/password credentials. To enforce credential check on the Proxy Adapters, a suitable configuration should be added in adapters.xml in the <data_provider> block, like this:
```xml
  <data_provider>
    ...
    <param name="auth">Y</param>
    <param name="auth.credentials.1.user">user1</param>
    <param name="auth.credentials.1.password">pwd1</param>
    ...
  </data_provider>
```
and the same should be added in the <metadata_provider> block.

See the configuration details in the [provided template](https://lightstreamer.com/docs/ls-ARI/latest/adapter_robust_conf_template/adapters.xml).
The provided source code is already predisposed for credential submission on both adapters.
You can rerun the adapters with the new configuration by launching:<BR/>
 ```sh 
 ./mvnw spring-boot:run "-Dspring-boot.run.arguments=--server.name=chat-spring --server.user=user1 --server.password=pwd1"
 ```
Authentication can (and should) be combined with TLS encryption.

## See Also

*    [Lightstreamer Java Remote Adapter API](https://www.lightstreamer.com/api/ls-adapter-remote/latest/)

### Clients Using This Adapter

*    [Lightstreamer - Basic Chat Demo - HTML Client](https://github.com/Lightstreamer/Lightstreamer-example-Chat-client-javascript)

### Related Projects

*    [Lightstreamer - Basic Chat Demo - Java Adapter](https://github.com/Lightstreamer/Lightstreamer-example-Chat-adapter-java)

## Lightstreamer Compatibility Notes

* Compatible with Lightstreamer SDK for Java Remote Adapters version 1.7 or newer and Lightstreamer Server version 7.4 or newer.
