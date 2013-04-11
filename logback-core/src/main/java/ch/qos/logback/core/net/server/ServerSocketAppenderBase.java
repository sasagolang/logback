/**
 * Logback: the reliable, generic, fast and flexible logging framework.
 * Copyright (C) 1999-2011, QOS.ch. All rights reserved.
 *
 * This program and the accompanying materials are dual-licensed under
 * either the terms of the Eclipse Public License v1.0 as published by
 * the Eclipse Foundation
 *
 *   or (per the licensee's choosing)
 *
 * under the terms of the GNU Lesser General Public License version 2.1
 * as published by the Free Software Foundation.
 */
package ch.qos.logback.core.net.server;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

import javax.net.ServerSocketFactory;

import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.net.SocketAppenderBase;
import ch.qos.logback.core.spi.PreSerializationTransformer;

/**
 * 
 * This is the base class for module specific ServerSocketAppender 
 * implementations.
 * 
 * @author Carl Harris
 */
public abstract class ServerSocketAppenderBase<E> extends AppenderBase<E> {

  /**
   * Default {@link ServerSocket} backlog
   */
  public static final int DEFAULT_BACKLOG = 50;

  /** 
   * Default queue size used for each client
   */
  public static final int DEFAULT_CLIENT_QUEUE_SIZE = 100;
  
  private int port = SocketAppenderBase.DEFAULT_PORT;
  private int backlog = DEFAULT_BACKLOG;
  private int clientQueueSize = DEFAULT_CLIENT_QUEUE_SIZE;
  
  private String address;
  private ThreadPoolFactoryBean threadPool;

  private ExecutorService executor;
  private ServerRunner<RemoteReceiverClient> runner;

  @Override
  public void start() {
    if (isStarted()) return;
    try {
      ServerSocket socket = getServerSocketFactory().createServerSocket(
          getPort(), getBacklog(), getInetAddress());    
      ServerListener<RemoteReceiverClient> listener = createServerListener(socket);
      executor = getThreadPool().createExecutor();
      runner = createServerRunner(listener, executor);
      runner.setContext(getContext());
      executor.execute(runner);
      super.start();
    }
    catch (Exception ex) {
      addError("server startup error: " + ex, ex);
    }
  }
  
  protected ServerListener<RemoteReceiverClient> createServerListener(
      ServerSocket socket) {
    return new RemoteReceiverServerListener(socket);
  }
  
  protected ServerRunner<RemoteReceiverClient> createServerRunner(
      ServerListener<RemoteReceiverClient> listener,
      Executor executor) {
    return new RemoteReceiverServerRunner(listener, executor, 
        getClientQueueSize());
  }
  
  @Override
  public void stop() {
    if (!isStarted()) return;
    try {
      runner.stop();
      executor.shutdownNow();
      super.stop();
    }
    catch (IOException ex) {
      addError("server shutdown error: " + ex, ex);
    }
  }

  @Override
  protected void append(E event) {
    if (event == null) return;
    postProcessEvent(event);
    final Serializable serEvent = getPST().transform(event);
    runner.accept(new ClientVisitor<RemoteReceiverClient>() {
      public void visit(RemoteReceiverClient client) {
        client.offer(serEvent);
      }      
    });
  }

  /**
   * Post process an event received via {@link #append(E)}.
   * @param event
   */
  protected abstract void postProcessEvent(E event);

  /**
   * Gets a transformer that will be used to convert a received event
   * to a {@link Serializable} form.
   * @return
   */
  protected abstract PreSerializationTransformer<E> getPST();

  /**
   * Gets the factory used to create {@link ServerSocket} objects.
   * <p>
   * The default implementation delegates to 
   * {@link ServerSocketFactory#getDefault()}.  Subclasses may override to
   * private a different socket factory implementation.
   * 
   * @return socket factory.
   */
  protected ServerSocketFactory getServerSocketFactory() throws Exception {
    return ServerSocketFactory.getDefault();
  }

  /**
   * Gets the local address for the listener.
   * @return an {@link InetAddress} representation of the local address.
   * @throws UnknownHostException
   */
  protected InetAddress getInetAddress() throws UnknownHostException {
    if (getAddress() == null) return null;
    return InetAddress.getByName(getAddress());
  }
  
  /**
   * Gets the local port for the listener.
   * @return local port
   */
  public int getPort() {
    return port;
  }

  /**
   * Sets the local port for the listener.
   * @param port the local port to set
   */
  public void setPort(int port) {
    this.port = port;
  }

  /**
   * Gets the listener queue depth.
   * <p>
   * This represents the number of connected clients whose connections 
   * have not yet been accepted.
   * @return queue depth
   * @see java.net.ServerSocket
   */
  public Integer getBacklog() {
    return backlog;
  }

  /**
   * Sets the listener queue depth.
   * <p>
   * This represents the number of connected clients whose connections 
   * have not yet been accepted.
   * @param backlog the queue depth to set
   * @see java.net.ServerSocket
   */
  public void setBacklog(Integer backlog) {
    this.backlog = backlog;
  }

  /**
   * Gets the local address for the listener.
   * @return a string representation of the local address
   */
  public String getAddress() {
    return address;
  }

  /**
   * Sets the local address for the listener.
   * @param address a host name or a string representation of an IP address
   */
  public void setAddress(String address) {
    this.address = address;
  }
  
  /**
   * Gets the event queue size used for each client connection. 
   * @return queue size
   */
  public int getClientQueueSize() {
    return clientQueueSize;
  }

  /**
   * Sets the event queue size used for each client connection.
   * @param clientQueueSize the queue size to set
   */
  public void setClientQueueSize(int clientQueueSize) {
    this.clientQueueSize = clientQueueSize;
  }

  /**
   * Gets the server's thread pool configuration.
   * @return thread pool configuration; if no thread pool configuration was
   *    provided, a default configuration is returned
   */
  public ThreadPoolFactoryBean getThreadPool() {
    if (threadPool == null) {
      return new ThreadPoolFactoryBean();
    }
    return threadPool;
  }

  /**
   * Sets the server's thread pool configuration.
   * @param threadPool the configuration to set
   */
  public void setThreadPool(ThreadPoolFactoryBean threadPool) {
    this.threadPool = threadPool;
  }

}

