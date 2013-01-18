
Hilda
======

Project updater, deployment, and management tool. Support Git and Rsync protocol too keep your projects up-to-date.

For Hilda >= 0.1.0 default hilda home not in User.home `~/.hilda` but located in `/etc/hilda`, you can always override
this behaviour by setting environment variable `HILDA_HOME` pointing to your own path.

Upgrade
---------

If you already using Hilda < 0.1.0 on your system, you needs to move current hilda home to `/etc/hilda`:

   $ sudo mv ~/.hilda /etc/hilda


Usage
-------

    $ hilda update

And all of your project/modules will be updated with no time.

    $ hilda state

To get current state in all of projects.


Installation
-------------


	$ git clone git://github.com/anvie/hilda.git
	$ cd hilda
	$ sbt update
	$ sbt assembly
	$ java -jar target/scala_xxx/hilda_xxxx.jar install

Create new projects/modules interactively:

	$ hilda configure


