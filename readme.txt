用法：

1.先安装jdk7

2.然后到conf文件夹里改一下端口号

3.如果需要运行php请启动php的fastcgi：
php-cgi.exe -b 127.0.0.1:9000 -c d:/server/php.ini

4.启动server
java -jar myserver.jar
linux下需要root权限

看到server start on 127.0.0.1:80说明启动成功了，可以打开浏览器查看。