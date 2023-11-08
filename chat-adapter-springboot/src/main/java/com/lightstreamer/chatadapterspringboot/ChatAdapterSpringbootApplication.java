package com.lightstreamer.chatadapterspringboot;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.lightstreamer.adapters.remote.DataProviderServer;
import com.lightstreamer.adapters.remote.MetadataProviderServer;
import com.lightstreamer.log.LogManager;
import com.lightstreamer.log.Logger;

@SpringBootApplication
public class ChatAdapterSpringbootApplication implements ApplicationRunner {

	public static final String PREFIX1 = "-";
	public static final String PREFIX2 = "/";

	public static final char SEP = '=';

	public static final String ARG_HELP_LONG = "help";
	public static final String ARG_HELP_SHORT = "?";

	public static final String ARG_HOST = "host";
	public static final String ARG_TLS = "tls"; // will use lowercase
	public static final String ARG_NOVERIFY = "noverify"; // will use lowercase
	public static final String ARG_METADATA_RR_PORT = "metadata_rrport";
	public static final String ARG_DATA_RR_PORT = "data_rrport";
	public static final String ARG_USER = "user";
	public static final String ARG_PASSWORD = "password";
	public static final String ARG_NAME = "name";

	Map<String, String> parameters = new HashMap<String, String>();

	@Value("${server.host:myurl.io}")
	private String host = null;

	@Value("${server.tls:false}")
	private String tls;

	@Value("${server.hostnameverified:false}")
	private String hostnameVerify;

	@Value("${server.metadata.port}")
	private String metadataport;

	@Value("${server.data.port}")
	private String dataport;

	@Value("${server.name}")
	private String name = "";

	@Value("${data.flush.interval:0}")
	private String flushi;

	@Value("${server.user:}")
	private String username = null;

	@Value("${server.password:}")
	String password = null;

	private boolean isTls;
	private boolean isHostnameVerify;
	int rrPortMD = -1;
	int rrPortD = -1;

	private static Logger LOG = LogManager.getLogger(ChatAdapterSpringbootApplication.class.toString());

	public static void main(String[] args) {
		SpringApplication application = new SpringApplication(ChatAdapterSpringbootApplication.class);
		application.setWebApplicationType(WebApplicationType.NONE);
		application.run(args);
	}

	@Override
	public void run(ApplicationArguments args) throws Exception {
		System.out.println("Start Lightstreamer Chat remote Adapter ... ");

		System.out.println("Lightstreamer server hostname: " + host);

		isTls = Boolean.parseBoolean(tls);
		System.out.println("Lightstreamer server use tls?: " + isTls);

		isHostnameVerify = Boolean.parseBoolean(hostnameVerify);
		System.out.println("Lightstreamer server use hostname verified?: " + isHostnameVerify);

		rrPortMD = Integer.parseInt(metadataport);
		System.out.println("Lightstreamer server use tls?: " + rrPortMD);

		rrPortD = Integer.parseInt(dataport);
		System.out.println("Lightstreamer server use tls?: " + rrPortD);

		System.out.println("Lightstreamer server name: " + name);

		System.out.println("Data Adapter flush chat history interval: " + flushi);

		System.out.println("Start Lightstreamer Chat remote Adapter ... ");

		/*
		 * 
		 * Metadata Adapter Starter
		 * 
		 */
		MetadataProviderServer metaserver = new MetadataProviderServer();
		metaserver.setAdapter(new ChatMetaDataAdapter(name));

		if (name != null) {
			metaserver.setName(name);
		}
		if (username.length() > 0) {

			System.out.println("Credentials: " + username + ", " + password);

			metaserver.setRemoteUser(username);
			metaserver.setRemotePassword(password);
		}

		System.out.println("Remote Metadata Adapter initialized");

		ServerStarter starter = new ServerStarter(host, isTls, isHostnameVerify, rrPortMD);
		starter.launch(metaserver);

		/*
		 * 
		 * Data Adapter Starter
		 * 
		 */
		ServerStarter data_starter = new ServerStarter(host, isTls, isHostnameVerify, rrPortD);

		DataProviderServer server = new DataProviderServer();
		server.setAdapter(new ChatDataAdapter(name, flushi));

		if (name != null) {
			server.setName(name);
		}
		if (username != null) {
			server.setRemoteUser(username);
			server.setRemotePassword(password);
		}

		System.out.println("Remote Data Adapter initialized");

		data_starter.launch(server);

	}
}
