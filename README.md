# ![Image](https://www.knime.com/files/knime_logo_github_40x40_4layers.png) KNIME� - Email Processing Integration

KNIME Analytics Platform - Email Processing Integration is designed to manage emails from within a KNIME workflow e.g. 
downloading emails including attachments and moving processed emails into another email folder. The extension also 
provides a new email session port type which allows to decouple the email server login from the different email 
management tasks.

### Content
This repository contains the source code for KNIME - Email Processing Integration. The code is organized as follows:

* _org.knime.email_: The main plugin with the email processing nodes (connector, reader, mover) and the new 
email session port.
* _org.knime.email.tests_: Unit tests for the different features in the _org.knime.email_ plugin based on 
[GreenMail](https://greenmail-mail-test.github.io/greenmail/#).
* _org.knime.email.tests.janitor_: KNIME testing janitor implementation that exposes an in-memory email server using
[GreenMail](https://greenmail-mail-test.github.io/greenmail/#).

### Development Notes
You can find instructions on how to work with our code or develop extensions for KNIME Analytics Platform in the _knime-sdk-setup_ repository on [BitBucket](https://bitbucket.org/KNIME/knime-sdk-setup) or [GitHub](http://github.com/knime/knime-sdk-setup).

### Join the Community!
* [KNIME Forum](https://forum.knime.com/)