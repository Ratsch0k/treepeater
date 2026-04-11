# Treepeater
Treepeater is a Burp Suite extension that lets you organize your requests in a tree structure, making it easy to mirror the layout of a real website or API.
You can group related endpoints together and nest them as deeply as needed, which is especially useful for large APIs with long, hierarchical paths.
Nodes can be freely reordered by dragging and dropping them anywhere in the tree.

Use statuses to annotate your requests and quickly identify what matters.
For example, the built-in default statuses let you flag requests that still need testing, mark them as completed, or highlight requests where you found a vulnerability.
Statuses support custom names, colors, and icons, and are color-coded in the tree so you can differentiate them at a glance.
You can also set a default status list that is applied automatically to new requests.

Each request panel keeps a full history of everything you have sent, and you can navigate back and forward through previous requests and their responses without losing any of your work.

Treepeater closely mirrors the Repeater UI to minimize the learning curve.
If you are already familiar with Repeater, you should be productive in Treepeater almost immediately.

Almost every action has a configurable keyboard shortcut — sending requests, navigating history, switching tabs, renaming nodes, changing statuses, and more.
Treepeater aims to eventually support a fully keyboard-driven workflow, though there is still work to do in that area.

Several more features are planned, including an agent-based tester, an encoder/decoder, search-and-replace, diff view, split views, and a payload snippet library.

## Contents
* [Before you start](#before-you-start)
* [Writing your extension](#writing-your-extension)
* [Building your extension](#building-your-extension)
* [Loading the JAR file into Burp](#loading-the-jar-file-into-burp)
* [Sharing your extension](#sharing-your-extension)


## Before you start

Before you begin development, make sure that your project's JDK is set to version "21".

## Building your extension

When you're ready to test and use your extension, follow these steps to build a JAR file and load it into Burp.

### Building the JAR file

To build the JAR file, run the following command in the root directory of this project:

* For UNIX-based systems: `./gradlew jar`
* For Windows systems: `gradlew jar`

If successful, the JAR file is saved to `<project_root_directory>/build/libs/<project_name>.jar`. If the build fails, errors are shown in the console. By default, the project name is `extension-template-project`. You can change this in the [settings.gradle.kts](./settings.gradle.kts) file.


## Loading the JAR file into Burp

To load the JAR file into Burp:

1. In Burp, go to **Extensions > Installed**.
2. Click **Add**.
3. Under **Extension details**, click **Select file**.
4. Select the JAR file you just built, then click **Open**.
5. [Optional] Under **Standard output** and **Standard error**, choose where to save output and error messages.
6. Click **Next**. The extension is loaded into Burp.
7. Review any messages displayed in the **Output** and **Errors** tabs.
8. Click **Close**.

Your extension is loaded and listed in the **Burp extensions** table. You can test its behavior and make changes to the code as necessary.

### Reloading the JAR file in Burp

If you make changes to the code, you must rebuild the JAR file and reload your extension in Burp for the changes to take effect.

To rebuild the JAR file, follow the steps for [building the JAR file](#building-the-jar-file).

To quickly reload your extension in Burp:

1. In Burp, go to **Extensions > Installed**.
2. Hold `Ctrl` or `⌘`, and select the **Loaded** checkbox next to your extension.
