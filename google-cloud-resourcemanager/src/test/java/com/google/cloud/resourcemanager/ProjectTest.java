/*
 * Copyright 2015 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.resourcemanager;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.createStrictMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.google.cloud.Identity;
import com.google.cloud.Policy;
import com.google.cloud.Role;
import com.google.cloud.resourcemanager.ProjectInfo.ResourceId;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;

public class ProjectTest {
  private static final String PROJECT_ID = "project-id";
  private static final String NAME = "myProj";
  private static final Map<String, String> LABELS = ImmutableMap.of("k1", "v1", "k2", "v2");
  private static final Long PROJECT_NUMBER = 123L;
  private static final Long CREATE_TIME_MILLIS = 123456789L;
  private static final ProjectInfo.State STATE = ProjectInfo.State.DELETE_REQUESTED;
  private static final ProjectInfo PROJECT_INFO = ProjectInfo.builder(PROJECT_ID)
      .name(NAME)
      .labels(LABELS)
      .projectNumber(PROJECT_NUMBER)
      .createTimeMillis(CREATE_TIME_MILLIS)
      .state(STATE)
      .build();
  private static final Identity USER = Identity.user("abc@gmail.com");
  private static final Identity SERVICE_ACCOUNT =
      Identity.serviceAccount("service-account@gmail.com");
  private static final Policy POLICY = Policy.builder()
      .addIdentity(Role.owner(), USER)
      .addIdentity(Role.editor(), SERVICE_ACCOUNT)
      .build();

  private ResourceManager serviceMockReturnsOptions = createStrictMock(ResourceManager.class);
  private ResourceManagerOptions mockOptions = createMock(ResourceManagerOptions.class);
  private ResourceManager resourceManager;
  private Project expectedProject;
  private Project project;

  @Before
  public void setUp() {
    resourceManager = createStrictMock(ResourceManager.class);
  }

  @After
  public void tearDown() throws Exception {
    verify(resourceManager);
  }

  private void initializeExpectedProject(int optionsCalls) {
    expect(serviceMockReturnsOptions.options()).andReturn(mockOptions).times(optionsCalls);
    replay(serviceMockReturnsOptions);
    expectedProject =
        new Project(serviceMockReturnsOptions, new ProjectInfo.BuilderImpl(PROJECT_INFO));
  }

  private void initializeProject() {
    project = new Project(resourceManager, new ProjectInfo.BuilderImpl(PROJECT_INFO));
  }

  @Test
  public void testToBuilder() {
    initializeExpectedProject(4);
    replay(resourceManager);
    compareProjects(expectedProject, expectedProject.toBuilder().build());
  }

  @Test
  public void testBuilder() {
    expect(resourceManager.options()).andReturn(mockOptions).times(7);
    replay(resourceManager);
    Project.Builder builder =
        new Project.Builder(new Project(resourceManager, new ProjectInfo.BuilderImpl("wrong-id")));
    Project project = builder.projectId(PROJECT_ID)
        .name(NAME)
        .labels(LABELS)
        .projectNumber(PROJECT_NUMBER)
        .createTimeMillis(CREATE_TIME_MILLIS)
        .state(STATE)
        .build();
    assertEquals(PROJECT_ID, project.projectId());
    assertEquals(NAME, project.name());
    assertEquals(LABELS, project.labels());
    assertEquals(PROJECT_NUMBER, project.projectNumber());
    assertEquals(CREATE_TIME_MILLIS, project.createTimeMillis());
    assertEquals(STATE, project.state());
    assertEquals(resourceManager.options(), project.resourceManager().options());
    assertNull(project.parent());
    ResourceId parent = new ResourceId("id", "type");
    project = project.toBuilder()
        .clearLabels()
        .addLabel("k3", "v3")
        .addLabel("k4", "v4")
        .removeLabel("k4")
        .parent(parent)
        .build();
    assertEquals(PROJECT_ID, project.projectId());
    assertEquals(NAME, project.name());
    assertEquals(ImmutableMap.of("k3", "v3"), project.labels());
    assertEquals(PROJECT_NUMBER, project.projectNumber());
    assertEquals(CREATE_TIME_MILLIS, project.createTimeMillis());
    assertEquals(STATE, project.state());
    assertEquals(resourceManager.options(), project.resourceManager().options());
    assertEquals(parent, project.parent());
  }

  @Test
  public void testGet() {
    initializeExpectedProject(1);
    expect(resourceManager.get(PROJECT_INFO.projectId())).andReturn(expectedProject);
    replay(resourceManager);
    Project loadedProject = resourceManager.get(PROJECT_INFO.projectId());
    assertEquals(expectedProject, loadedProject);
  }

  @Test
  public void testReload() {
    initializeExpectedProject(2);
    ProjectInfo newInfo = PROJECT_INFO.toBuilder().addLabel("k3", "v3").build();
    Project expectedProject =
        new Project(serviceMockReturnsOptions, new ProjectInfo.BuilderImpl(newInfo));
    expect(resourceManager.options()).andReturn(mockOptions);
    expect(resourceManager.get(PROJECT_INFO.projectId())).andReturn(expectedProject);
    replay(resourceManager);
    initializeProject();
    Project newProject = project.reload();
    assertEquals(expectedProject, newProject);
  }

  @Test
  public void testLoadNull() {
    initializeExpectedProject(1);
    expect(resourceManager.get(PROJECT_INFO.projectId())).andReturn(null);
    replay(resourceManager);
    assertNull(resourceManager.get(PROJECT_INFO.projectId()));
  }

  @Test
  public void testReloadNull() {
    initializeExpectedProject(1);
    expect(resourceManager.options()).andReturn(mockOptions);
    expect(resourceManager.get(PROJECT_INFO.projectId())).andReturn(null);
    replay(resourceManager);
    Project reloadedProject =
        new Project(resourceManager, new ProjectInfo.BuilderImpl(PROJECT_INFO)).reload();
    assertNull(reloadedProject);
  }

  @Test
  public void testResourceManager() {
    initializeExpectedProject(1);
    replay(resourceManager);
    assertEquals(serviceMockReturnsOptions, expectedProject.resourceManager());
  }

  @Test
  public void testDelete() {
    initializeExpectedProject(1);
    expect(resourceManager.options()).andReturn(mockOptions);
    resourceManager.delete(PROJECT_INFO.projectId());
    replay(resourceManager);
    initializeProject();
    project.delete();
  }

  @Test
  public void testUndelete() {
    initializeExpectedProject(1);
    expect(resourceManager.options()).andReturn(mockOptions);
    resourceManager.undelete(PROJECT_INFO.projectId());
    replay(resourceManager);
    initializeProject();
    project.undelete();
  }

  @Test
  public void testReplace() {
    initializeExpectedProject(2);
    Project expectedReplacedProject = expectedProject.toBuilder().addLabel("k3", "v3").build();
    expect(resourceManager.options()).andReturn(mockOptions).times(2);
    expect(resourceManager.replace(anyObject(Project.class))).andReturn(expectedReplacedProject);
    replay(resourceManager);
    initializeProject();
    Project newProject =
        new Project(resourceManager, new ProjectInfo.BuilderImpl(expectedReplacedProject));
    Project actualReplacedProject = newProject.replace();
    compareProjectInfos(expectedReplacedProject, actualReplacedProject);
  }

  @Test
  public void testGetPolicy() {
    expect(resourceManager.options()).andReturn(mockOptions).times(1);
    expect(resourceManager.getPolicy(PROJECT_ID)).andReturn(POLICY);
    replay(resourceManager);
    initializeProject();
    assertEquals(POLICY, project.getPolicy());
  }

  @Test
  public void testReplacePolicy() {
    expect(resourceManager.options()).andReturn(mockOptions).times(1);
    expect(resourceManager.replacePolicy(PROJECT_ID, POLICY)).andReturn(POLICY);
    replay(resourceManager);
    initializeProject();
    assertEquals(POLICY, project.replacePolicy(POLICY));
  }

  @Test
  public void testTestPermissions() {
    List<Boolean> response = ImmutableList.of(true, true);
    String getPermission = "resourcemanager.projects.get";
    String deletePermission = "resourcemanager.projects.delete";
    expect(resourceManager.options()).andReturn(mockOptions).times(1);
    expect(resourceManager.testPermissions(
            PROJECT_ID, ImmutableList.of(getPermission, deletePermission)))
        .andReturn(response);
    replay(resourceManager);
    initializeProject();
    assertEquals(
        response, project.testPermissions(ImmutableList.of(getPermission, deletePermission)));
  }

  private void compareProjects(Project expected, Project value) {
    assertEquals(expected, value);
    compareProjectInfos(expected, value);
    assertEquals(expected.resourceManager().options(), value.resourceManager().options());
  }

  private void compareProjectInfos(ProjectInfo expected, ProjectInfo value) {
    assertEquals(expected.projectId(), value.projectId());
    assertEquals(expected.name(), value.name());
    assertEquals(expected.labels(), value.labels());
    assertEquals(expected.projectNumber(), value.projectNumber());
    assertEquals(expected.createTimeMillis(), value.createTimeMillis());
    assertEquals(expected.state(), value.state());
    assertEquals(expected.parent(), value.parent());
  }
}
