/*
 * Copyright 2014 Objectos, Fábrica de Software LTDA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package br.com.objectos.process;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.util.List;

import br.com.objectos.core.auto.AutoPojo;
import br.com.objectos.core.io.Directory;
import br.com.objectos.core.testing.Testable;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;

/**
 * @author marcio.endo@objectos.com.br (Marcio Endo)
 */
@AutoPojo
public abstract class Process implements Testable<Process> {

  private final Object processLock = new Object();

  private java.lang.Process process;
  private Lifecycle lifecycle;
  private volatile boolean running;

  abstract Directory workingDirectory();
  abstract String command();
  abstract List<String> argumentList();

  Process() {
  }

  public static ProcessBuilder builder() {
    return new ProcessBuilderPojo();
  }

  public void destroy() {
    synchronized (processLock) {
      if (process != null) {
        process.destroy();
      }
    }
  }

  public void ensureRunning() throws ProcessException {
    ensureRunning(NoopRestartCallback.INSTANCE);
  }

  public void ensureRunning(RestartCallback restartCallback) throws ProcessException {
    if (!running()) {
      synchronized (processLock) {
        if (!running()) {
          tryToStart();
          restartCallback.onRestart(this);
        }
      }
    }
  }

  public void kill() {
    if (lifecycle != null) {
      lifecycle.kill();
    }
  }

  public String readLine() throws ProcessException {
    String res = "";

    synchronized (processLock) {
      if (process != null) {
        try {
          InputStream in = process.getInputStream();
          InputStreamReader reader = new InputStreamReader(in);
          BufferedReader buffered = new BufferedReader(reader);
          res = buffered.readLine();
        } catch (IOException e) {
          String commandLine = Joiner.on(" ").join(commandLine());
          throw new ProcessException("Could read line from the process " + commandLine, e);
        }
      }
    }

    return res;
  }

  public boolean running() {
    return running;
  }

  public void start() throws ProcessException {
    synchronized (processLock) {
      if (process == null) {
        tryToStart();
      }
    }
  }

  public void stop() {
    synchronized (processLock) {
      if (process != null) {
        process.destroy();
      }
    }
  }

  public void write(String text) throws ProcessException {
    synchronized (processLock) {
      if (process != null) {
        try {
          OutputStream os = process.getOutputStream();
          OutputStreamWriter writer = new OutputStreamWriter(os);
          writer.write(text);
          writer.flush();
        } catch (IOException e) {
          String commandLine = Joiner.on(" ").join(commandLine());
          throw new ProcessException("Could write to the process " + commandLine, e);
        }
      }
    }
  }

  private void tryToStart() throws ProcessException {
    try {
      java.lang.ProcessBuilder processBuilder = new java.lang.ProcessBuilder(commandLine());
      processBuilder.directory(workingDirectory().fileAt("."));
      process = processBuilder.start();

      Class<?> processClass = process.getClass();
      String className = processClass.getName();
      if (className.equals("java.lang.UNIXProcess")) {
        lifecycle = new UnixLifecyle();
      } else {
        lifecycle = new UnknownLifecycle();
      }

      lifecycle.start();
    } catch (IOException e) {
      String commandLine = Joiner.on(" ").join(commandLine());
      throw new ProcessException("Could not start the process " + commandLine, e);
    }
  }

  private List<String> commandLine() {
    return ImmutableList.<String> builder()
        .add(command())
        .addAll(argumentList())
        .build();
  }

  private class UnixLifecyle extends Lifecycle {

    @Override
    void kill() {
      Optional<Integer> maybePid = pid();
      if (!maybePid.isPresent()) {
        return;
      }

      Integer pid = maybePid.get();
      List<String> argumentList = ImmutableList.<String> builder()
          .add("kill")
          .add("-9")
          .add(pid.toString())
          .build();
      java.lang.ProcessBuilder processBuilder = new java.lang.ProcessBuilder(argumentList);

      try {
        processBuilder.start().waitFor();
      } catch (InterruptedException e) {
        throw Throwables.propagate(e);
      } catch (IOException e) {
        throw Throwables.propagate(e);
      }
    }

    private Optional<Integer> pid() {
      try {
        Class<?> processClass = process.getClass();
        Field pidField = processClass.getDeclaredField("pid");
        pidField.setAccessible(true);
        Object value = pidField.get(process);
        return Optional.of((Integer) value);
      } catch (Exception e) {
        return Optional.absent();
      }
    }

  }

  private class UnknownLifecycle extends Lifecycle {
    @Override
    void kill() {
    }
  }

  private abstract class Lifecycle implements Runnable {

    @Override
    public void run() {
      try {
        running = true;
        process.waitFor();
      } catch (InterruptedException e) {
        // same as finally
      } finally {
        synchronized (processLock) {
          running = false;
          process = null;
          lifecycle = null;
        }
      }
    }

    public void start() {
      Thread thread = new Thread(this);
      thread.start();
    }

    abstract void kill();

  }

  private static enum NoopRestartCallback implements RestartCallback {
    INSTANCE;
    @Override
    public void onRestart(Process self) {
    }
  }

}