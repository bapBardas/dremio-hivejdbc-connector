/*
 * Copyright (C) 2017-2018 Dremio Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dremio.exec.store.jdbc.conf;

import static com.google.common.base.Preconditions.checkNotNull;

import javax.validation.constraints.NotBlank;
import java.util.Base64;
import com.dremio.exec.catalog.conf.DisplayMetadata;
import com.dremio.exec.catalog.conf.NotMetadataImpacting;
import com.dremio.exec.catalog.conf.Secret;
import com.dremio.exec.catalog.conf.SourceType;
import com.dremio.exec.store.jdbc.CloseableDataSource;
import com.dremio.exec.store.jdbc.DataSources;
import com.dremio.exec.store.jdbc.JdbcPluginConfig;
import com.dremio.exec.store.jdbc.dialect.arp.ArpDialect;
import com.dremio.options.OptionManager;
import com.dremio.services.credentials.CredentialsService;
import com.google.common.annotations.VisibleForTesting;

import io.protostuff.Tag;

/**
 * Configuration for SQLite sources.
 */
@SourceType(value = "HIVEJDBC", label = "HiveJDBC", uiConfig = "hivejdbc-layout.json", externalQuerySupported = true)
public class HiveJDBCConf extends AbstractArpConf<HiveJDBCConf> {
  private static final String ARP_FILENAME = "arp/implementation/hivejdbc-arp.yaml";
  private static final ArpDialect ARP_DIALECT =
      AbstractArpConf.loadArpFile(ARP_FILENAME, (ArpDialect::new));
  private static final String DRIVER = "com.cloudera.hive.jdbc.HS2Driver";

  @NotBlank
  @Tag(1)
  @DisplayMetadata(label = "Connection String")
  public String connectionString;

  @Tag(2)
  @DisplayMetadata(label = "Username")
  public String username;

  @Tag(3)
  @Secret
  @DisplayMetadata(label = "Password")
  public String password;

  @Tag(4)
  @DisplayMetadata(label = "Record fetch size")
  @NotMetadataImpacting
  public int fetchSize = 200;

//  If you've written your source prior to Dremio 16, and it allows for external query via a flag like below, you should
//  mark the flag as @JsonIgnore and remove use of the flag since external query support is now managed by the SourceType
//  annotation and if the user has been granted the EXTERNAL QUERY permission (enterprise only). Marking the flag as @JsonIgnore
//  will hide the external query tickbox field, but allow your users to upgrade Dremio without breaking existing source
//  configurations. An example of how to dummy this out is commented out below.
//  @Tag(3)
//  @NotMetadataImpacting
//  @JsonIgnore
//  public boolean enableExternalQuery = false;

  @Tag(5)
  @DisplayMetadata(label = "Maximum idle connections")
  @NotMetadataImpacting
  public int maxIdleConns = 8;

  @Tag(6)
  @DisplayMetadata(label = "Connection idle time (s)")
  @NotMetadataImpacting
  public int idleTimeSec = 60;

  @VisibleForTesting
  public String toJdbcConnectionString() {

    final String base64Separator = "base64,";
    final String connectionString = checkNotNull(this.connectionString, "Missing connection string.");
    final String userString = checkNotNull(this.username, "Missing username.");
    final String passwordBase64 = checkNotNull(this.password, "Missing password.").substring(this.password.lastIndexOf(base64Separator) + base64Separator.length());
    final byte[] passwordByte = Base64.getDecoder().decode(passwordBase64);
    final String passwordString = new String(passwordByte);

    return String.format("%s;user=%s;password=%s;", connectionString, userString, passwordString);
  }

  @Override
  @VisibleForTesting
  public JdbcPluginConfig buildPluginConfig(
          JdbcPluginConfig.Builder configBuilder,
          CredentialsService credentialsService,
          OptionManager optionManager
  ) {
    return configBuilder.withDialect(getDialect())
            .withDialect(getDialect())
            .withFetchSize(fetchSize)
            .withDatasourceFactory(this::newDataSource)
            .clearHiddenSchemas()
            //.addHiddenSchema("SYSTEM")
            .build();
  }

  private CloseableDataSource newDataSource() {
    return DataSources.newGenericConnectionPoolDataSource(DRIVER,
            toJdbcConnectionString(), null, null, null, DataSources.CommitMode.DRIVER_SPECIFIED_COMMIT_MODE,
            maxIdleConns, idleTimeSec);
  }

  @Override
  public ArpDialect getDialect() {
    return ARP_DIALECT;
  }

  @VisibleForTesting
  public static ArpDialect getDialectSingleton() {
    return ARP_DIALECT;
  }
}