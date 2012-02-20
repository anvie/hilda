
Hilda
======

Project updater, deployment, and management tool. Support Git and Rsync protocol too keep your projects up-to-date.


Usage
======

    $ hilda update

And all of your project/modules will be updated with no time.

    $ hilda state

To get current state in all of projects.


Installation
=============


	$ git clone git://github.com/anvie/hilda.git
	$ cd hilda
	$ sbt update
	$ sbt assembly
	$ java -jar target/scala_xxx/hilda_xxxx.jar install

Create new projects/modules interactively:

	$ hilda configure


