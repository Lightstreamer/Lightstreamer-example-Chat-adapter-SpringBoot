package com.lightstreamer.chatadapterspringboot;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.GeneralSecurityException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import com.lightstreamer.adapters.remote.DataProviderException;
import com.lightstreamer.adapters.remote.ExceptionHandler;
import com.lightstreamer.adapters.remote.MetadataProviderException;
import com.lightstreamer.adapters.remote.RemotingException;
import com.lightstreamer.adapters.remote.Server;
import com.lightstreamer.log.LogManager;

public class ServerStarter implements ExceptionHandler, Runnable {

    private static com.lightstreamer.log.Logger _log = LogManager
            .getLogger("LS_demos_Logger.StockQuotes.ServerStarter");

    private Server _server;
    private boolean _closed;

    private String _host;
    private boolean _isTls;
    private boolean _isHostnameVerify;
    private int _rrPort;

    public ServerStarter(String host, boolean isTls, boolean isHostnameVerify, int rrPort) {
        _host = host;
        _isTls = isTls;
        _isHostnameVerify = isHostnameVerify;
        _rrPort = rrPort;
    }

    public final void launch(Server server) {
        _server = server;
        _closed = false;
        _server.setExceptionHandler(this);
        Thread t = new Thread(this);
        t.start();
    }

    public final void run() {
        Socket _rrSocket = null;

        do {
            _log.info("Connecting...");

            try {
                if (_host != null) {
                    _rrSocket = createProperSocket(_host, _isTls, _isHostnameVerify, _rrPort);
                } else {
                    _rrSocket = acceptProperSocket(_isTls, _rrPort);
                }
                _server.setRequestStream(_rrSocket.getInputStream());
                _server.setReplyStream(_rrSocket.getOutputStream());

                _log.info("Connected");
                break;

            } catch (IOException | GeneralSecurityException e) {
                _log.info("Connection failed: " + e);
                _log.warn("Connection failed, retrying in 10 seconds...");
                try {
                    if (_rrSocket != null) {
                        _rrSocket.close();
                    }
                } catch (IOException e1) {
                }

                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e1) {
                }
            }

        } while (true);

        try {
            _server.start();

        } catch (DataProviderException | MetadataProviderException | RemotingException e) {
            _log.fatal("Exception caught while starting the server: " + e.getMessage() + ", aborting...", e);
            _server.close();
            System.exit(1);
        }
    }

    private static Socket createProperSocket(String host, boolean isTls, boolean isHostnameVerify, int port)
            throws IOException {
        _log.info("Opening connection on port " + port + (isTls ? " with TLS" : "") + "...");
        Socket s = null;
        try {
            if (isTls) {
                SSLSocket socket = (SSLSocket) SSLSocketFactory.getDefault().createSocket(host, port);
                socket.startHandshake();
                if (isHostnameVerify) {
                    // hostname check; could be disabled to simplify tests
                    HostnameVerifier hv = HttpsURLConnection.getDefaultHostnameVerifier();
                    SSLSession ssls = socket.getSession();
                    if (!hv.verify(host, ssls)) {
                        _log.error("Name verification failed: found " + ssls.getPeerPrincipal());
                        socket.close();
                        throw new IOException("Name verification failed");
                    }
                }
                s = socket;
            } else {
                s = new Socket(host, port);
            }
        } finally {
            if (s != null) {
                _log.info("Connection on port " + port + " opened");
            } else {
                _log.info("Connection on port " + port + " failed");
            }
        }
        return s;
    }

    private static Socket acceptProperSocket(boolean isTls, int port) throws IOException, GeneralSecurityException {
        _log.info("Listening on port " + port + (isTls ? " with TLS" : "") + "...");
        Socket s = null;
        ServerSocket serverSocket = null;
        try {
            if (isTls) {
                SSLServerSocketFactory factory = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
                // leans on the following java system properties to be configured:
                // - javax.net.ssl.keyStore
                // - javax.net.ssl.keyStorePassword
                serverSocket = factory.createServerSocket(port);
                if (false) {
                    // possible further authentication challenge on the Proxy Adapter
                    ((SSLServerSocket) serverSocket).setNeedClientAuth(true);
                }
            } else {
                serverSocket = new ServerSocket(port);
            }
            s = serverSocket.accept();
            if (isTls) {
                ((SSLSocket) s).startHandshake();
            }
        } finally {
            if (s != null) {
                _log.info("Connection on port " + port + " opened");
            } else {
                _log.info("Connection on port " + port + " failed");
            }
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    _log.warn("Issue while closing the server socket on port " + port, e);
                }
            }
        }
        return s;
    }

    @Override
    public boolean handleIOException(IOException exception) {
        synchronized (this) {
            if (_closed) {
                return false;
            } else {
                _log.error("Connection to Lightstreamer Server closed", exception);
                _closed = true;
            }
        }
        _server.close();
        System.exit(0);
        return false;
    }

    @Override
    public boolean handleException(RemotingException exception) {
        synchronized (this) {
            if (_closed) {
                return false;
            } else {
                _log.error("Caught exception: " + exception.getMessage(), exception);
                _closed = true;
            }
        }
        _server.close();
        System.exit(1);
        return false;
    }

    // Notes about exception handling.
    //
    // In case of exception, the whole Remote Server process instance
    // can no longer be used;
    // closing it also ensures that the Proxy Adapter closes
    // (thus causing Lightstreamer Server to close)
    // or recovers by accepting connections from a new Remote
    // Server process instance.
    //
    // Keeping the process instance alive and replacing the Server
    // class instances would be possible.
    // This would issue new connections with Lightstreamer Server.
    // However, new instances of the Remote Adapters would also be needed
    // and a cleanup of the current instances should be performed,
    // by invoking them directly through a custom method.
}
