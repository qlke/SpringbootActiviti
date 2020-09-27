package boot.spring.controller;

import javax.servlet.http.HttpSession;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import boot.spring.pagemodel.MSG;
import boot.spring.service.LoginService;

@Controller
public class UserController {

    private LoginService loginservice;

    public UserController(LoginService loginservice) {
        this.loginservice = loginservice;
    }

    //登录验证
    @RequestMapping(value = "/loginValidate", method = RequestMethod.POST)
    public String loginValidate(
            @RequestParam("username") String username,
            @RequestParam("password") String pwd,
            HttpSession httpSession
    ) {
        if (username == null) return "login";
        String realPwd = loginservice.getpwdbyname(username);
        if (realPwd != null && pwd.equals(realPwd)) {
            httpSession.setAttribute("username", username);
            return "index";
        } else return "fail";
    }

    //进入到登录页面
    @RequestMapping(value = "/", method = RequestMethod.GET)
    public String index() {
        return "login";
    }

    //登出系统
    @RequestMapping(value = "/logout", method = RequestMethod.GET)
    public String logout(HttpSession httpSession) {
        httpSession.removeAttribute("username");
        return "login";
    }

    @RequestMapping(value = "/currentUser", method = RequestMethod.GET)
    @ResponseBody
    public MSG currentUser(HttpSession httpSession) {
        String userId = (String) httpSession.getAttribute("username");
        return new MSG(userId);
    }
}
