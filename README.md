# cpdog

#### 介绍
H5开源web聊天,目前已经完成了用户的注册、登录、添加好友、删除好友、

查询好友、添加聊天、显示聊天、上传用户图片、修改用户备注功能、

和后端数据交互使用的是websocket .前后端信息交互主要使用json格式.上传文件使

用的是自定的规则...后端完全基于原生的java-NIO实现....

自定义的前后端JSON数据交互协议例:

```js
const queryCurrentFriends ={
    uuid:"",
    url:"queryUsersFriends",
    src:"queryUsersFriends",
    tar:"queryUsersFriends",
    charts:{
        nikeName:"",
        currentPage:1,
        totalPage:1,
        currentActiveId:"m1-friends",
        tokenSession:websktoken
    }
}
```
#### 版本说明
05-01日: 更新了后端通知机制的设计.基于注解方式实现方法的调用.

05-12日: 加入TLS加密协议、使用原生SSLEngine实现.

05-15日: 加入了移动端的css样式.自适应

05-20日: 更新JDK版本为17、代码中并没有新的语法结构、可以根据需要自行更改配置...修复TLS握手阶段的数据留存问题

05-23日: 更新bytebuffer网络编码入站优化、使用官方提供的实现类.

#### 线上测试地址
https://www.kungreat.cn/
#### 详细介绍地址
https://space.bilibili.com/384704339
#### 界面图

1. 注册界面<img src="https://www.kungreat.cn/images/images_md/register.PNG" style="zoom:50%;" />
2. 登录 <img src="https://www.kungreat.cn/images/images_md/login.PNG" alt="login" style="zoom:50%;" />
3. 用户查询 <img src="https://www.kungreat.cn/images/images_md/users.PNG" alt="login" style="zoom:50%;" />

4.好友列表<img src="https://www.kungreat.cn/images/images_md/friends.PNG" alt="friends" style="zoom:50%;" />

5.用户设置 支持选择图片上传 和拖拉 上传图片

<img src="https://www.kungreat.cn/images/images_md/settings.PNG" alt="settings" style="zoom:50%;" />

6.聊天区域

<img src="https://www.kungreat.cn/images/images_md/viewsend.PNG" alt="viewsend" style="zoom:50%;" />

7:手机界面

<img src="https://www.kungreat.cn/images/images_md/pscts.PNG" alt="pscts" style="zoom:50%;" />

#### 软件架构

软件架构说明
基于Reactor 设计模式、使用原生java-nio实现的websocket 通信框架.

支持多线程、高并发、每个work一个线程绑定一个唯一的channel 减少不必要的并发性

完全基于NIO实现.最少的包装、协议的解释、数据的传输、尽量少的第三方依赖、

基于主流的设计、实现了程序@annotation注解化、通过反射动态的调用方法

[mysql、jackson、HikariCP、logback]

使用mysql数据库.jackson前后端数据交互.HikariCP数据库连接池.logback日志体系

加入了TLS加密协议、使用原生的SSLEngine完成、目前基本的通知机制也已经完成

#### 安装教程

1. 下载此项目源代码.

2. 初始化数据库表结构.sql脚本在 resources 下的mysql.txt

3. 修改cpdog.properties 配置相关的属性.用户存放图片地址要放在一个前端服务器

   的目录.后端接收到图片数据后会传到此目录下.

4. 扫描包路径  是后端服务接收到前端的请求数据标识后作一个后端方法的映射调用.

   如果不指定就默认从当前主方法的目录往下扫描.[请观看services包下的类.就是会扫描的

   类配置.后端接收到前端数据后.会根据前端传入的url解释对比后端的指定类的方法

   匹配后反射调用.有优先级.]

5. 修改logback.xml 日志文件配置、注意初始化  [日志文件存放路径]

6. 启动.可以通过开发工具打开**CpdogMain** 主方法启动. BOSS线程一个只接收连接.

   有连接后就把任务分配给WORK去完成.**new** **NioWorkServerSocket**[12];

   12就是你要创建的WORK对象的数量也就是多少个线程来工作、一个唯一的channel
   
   会绑定到一个唯一的work对象上.根据处理器情况设置线程数量.

   可以通过setOption 来设置NIO的SOCKET

   也可以通过maven 打成JAR包执行.打成的JAR包目录下有个lib、需要这二个才可以运行.

   lib就是依赖的第三方JAR包存放的目录.在生成的JAR包时的MF文件里已经关联上了.

   只需要把它们放在一个目录下就可以执行

   <img src="https://www.kungreat.cn/images/images_md/package.PNG" alt="package" style="zoom:50%;" />

#### 使用说明

1.  需要配合前端使用.
2.  https://github.com/mydreams2018/dwbbs


#### 感谢 jetbrains 提供的开源版 免费许可证
   <img src="https://www.kungreat.cn/images/images_md/jeb2.PNG" alt="package" style="zoom:50%;" />