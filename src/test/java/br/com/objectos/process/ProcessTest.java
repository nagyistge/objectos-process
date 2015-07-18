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

import static com.google.common.collect.Lists.newArrayList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.util.List;

import br.com.objectos.core.io.Directory;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author marcio.endo@objectos.com.br (Marcio Endo)
 */
public class ProcessTest {

  private Process ps;

  @BeforeMethod
  public void start() throws ProcessException {
    ps = Process.builder()
        .workingDirectory(Directory.JAVA_IO_TMPDIR)
        .command("/bin/cat")
        .argumentList(ImmutableList.<String> of())
        .build();
    ps.start();
    sleep();
  }

  @AfterMethod(alwaysRun = true)
  public void stop() {
    ps.stop();
  }

  @Test
  public void is_running() {
    assertThat(ps.running(), is(true));
  }

  @Test
  public void ensure_running_should_restart() throws ProcessException {
    assertThat(ps.running(), is(true));
    ps.kill();
    sleep();
    assertThat(ps.running(), is(false));
    ps.ensureRunning();
    sleep();
    assertThat(ps.running(), is(true));
  }

  @Test
  public void ensure_running_should_not_restart() throws ProcessException {
    assertThat(ps.running(), is(true));
    ps.ensureRunning();
    sleep();
    assertThat(ps.running(), is(true));
  }

  @Test
  public void ensure_running_and_callback() throws ProcessException {
    final List<Object> calledBack = newArrayList();
    assertThat(ps.running(), is(true));
    ps.kill();
    sleep();
    assertThat(ps.running(), is(false));
    ps.ensureRunning(new RestartCallback() {
      @Override
      public void onRestart(Process self) {
        calledBack.add(new Object());
      }
    });
    assertThat(calledBack.isEmpty(), is(false));
  }

  @Test
  public void ensure_running_and_NOT_callback() throws ProcessException {
    final List<Object> calledBack = newArrayList();
    assertThat(ps.running(), is(true));
    ps.ensureRunning(new RestartCallback() {
      @Override
      public void onRestart(Process self) {
        calledBack.add(new Object());
      }
    });
    assertThat(calledBack.isEmpty(), is(true));
  }

  @Test
  public void read_write() throws ProcessException {
    ps.write("first\n\n");
    assertThat(ps.readLine(), equalTo("first"));
    ps.write("second\n\n");
    assertThat(ps.readLine(), equalTo("second"));
  }

  private void sleep() {
    try {
      Thread.sleep(20);
    } catch (InterruptedException e) {
      throw Throwables.propagate(e);
    }
  }

}