# Bitwarden Credentials Provider Plugin

[![Jenkins Plugin](https://img.shields.io/jenkins/plugin/v/bitwarden-credentials-provider.svg)](https://plugins.jenkins.io/bitwarden-credentials-provider)
[![GitHub release](https://img.shields.io/github/release/jenkinsci/bitwarden-credentials-provider-plugin.svg?label=release)](https://github.com/jenkinsci/bitwarden-credentials-provider-plugin/releases/latest)
[![Jenkins Plugin Installs](https://img.shields.io/jenkins/plugin/i/bitwarden-credentials-provider.svg?color=blue)](https://plugins.jenkins.io/bitwarden-credentials-provider)

The **Bitwarden Credentials Provider** is a [Jenkins](https://jenkins.io) plugin that dynamically exposes every item in your [Bitwarden](https://bitwarden.com/) vault as a native Jenkins credential. It allows pipeline authors to access any secret on the fly by its name or ID, without requiring an administrator to pre-configure mappings in the Jenkins UI.

This "read-only, implicitly exposed" model dramatically simplifies credential management in a CI/CD environment.

## Table of Contents

- [How It Works](#how-it-works)
- [Getting Started](#getting-started)
- [Usage in Pipeline](#usage-in-pipeline)
- [Supported Credential Types](#supported-credential-types)
- [Configuration as Code (JCasC)](#configuration-as-code-jcasc)
- [License](#license)
- [Disclaimer](#disclaimer)

## How It Works

This plugin uses the Bitwarden CLI (`bw`) to interact with your vault.
A high-performance, thread-safe session manager ensures that the slow process of logging in and unlocking the vault is performed only once.
The resulting session token is cached securely in memory and reused across all concurrent builds.

On every credential request, the plugin:

1.  Gets a valid session token from the cache (or creates one if it's the first run).
2.  Forces a `bw sync` to ensure the local data is current.
3.  Fetches the *entire list* of items from the vault.
4.  Dynamically converts each Bitwarden item into a native Jenkins credential, making it available to your jobs.

**Note** This credential provider plugin does *not* support providing Bitwarden credentials within the Jenkins UI for configuring other plugins, it only supports providing credentials from Bitwarden within pipelines or groovy scripts.

## Getting Started

### 1. Prerequisites

The Bitwarden CLI (`bw`) must be installed and available in the `PATH` on the Jenkins controller machine or container, for example:

```bash
wget -O bw.zip 'https://bitwarden.com/download/?app=cli&platform=linux' && \
unzip bw.zip && \
install bw /usr/local/bin/ && \
rm bw.zip bw
```


### 2. Plugin Configuration

You must first configure the plugin's global settings in **Manage Jenkins > Configure System**.

![Jenkins Global Configuration UI](https://placehold.co/800x300/f0f0f0/333?text=Jenkins+Global+Configuration+UI)

-   **Bitwarden Server URL:** For self-hosted instances like Vaultwarden. Leave blank for the official Bitwarden cloud.
-   **Bitwarden API Key Credential:** Select a Jenkins "Username with password" credential that stores your Bitwarden service account's Client ID and Client Secret.
-   **Bitwarden Master Password Credential:** Select a Jenkins "Secret text" credential that stores your service account's Master Password.

## Usage in Pipeline

This plugin makes every item in your vault available as a Jenkins credential. For maximum flexibility, each Bitwarden item is exposed **twice**:

1.  Once where the `credentialsId` is the item's **Name**.
2.  Once where the `credentialsId` is the item's **UUID**.

This allows you to choose whether to reference a secret by its human-readable name or its unique, stable ID.

**Example: Fetching a Secure Note by its Name**

```groovy
// Jenkinsfile
withCredentials([string(credentialsId: 'My Production API Key', variable: 'API_KEY')]) {
    sh 'echo "The secret API key is $API_KEY"'
}
```

**Example: Fetching a Login by its UUID**

```groovy
// Jenkinsfile
withCredentials([usernamePassword(credentialsId: 'a1b2c3d4-e5f6-7890-a1b2-c3d4e5f67890', usernameVariable: 'USER', passwordVariable: 'PASS')]) {
    sh 'echo "Logging in with user $USER"'
}
```

## Supported Credential Types

The plugin can automatically convert Bitwarden items into the following Jenkins credential types based on their content.

| Bitwarden Item Type | Jenkins Credential Type               | Notes                                                              |
| ------------------- |---------------------------------------| ------------------------------------------------------------------ |
| Login               | `StandardUsernamePasswordCredentials` |                                                                    |
| Secure Note         | `StringCredentials`                   | The default for any secure note.                                   |
| Secure Note         | `FileCredentials`                     | If the note's name ends with `.env`. Useful for Docker Compose.    |
| SSH Key             | `SSHUserPrivateKey`                   | The username is parsed from the public key's comment field.        |

## Configuration as Code (JCasC)

You can fully configure this plugin's global settings via JCasC.

```yaml
unclassified:
  bitwardenGlobalConfig:
    serverUrl: "[https://vaultwarden.example.com](https://vaultwarden.example.com)"
    apiCredentialId: "bitwarden-api-key"
    masterPasswordCredentialId: "bitwarden-master-password"
```

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

## Disclaimer

This repository is provided as-is. The author assumes no responsibility for any errors or omissions in the content or for any consequences that may arise from the use of the information or software provided. Always exercise caution and seek professional advice if necessary.
