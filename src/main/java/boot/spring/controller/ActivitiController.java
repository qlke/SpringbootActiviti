package boot.spring.controller;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.activiti.bpmn.model.BpmnModel;
import org.activiti.engine.HistoryService;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.history.HistoricActivityInstance;
import org.activiti.engine.history.HistoricProcessInstance;
import org.activiti.engine.history.HistoricProcessInstanceQuery;
import org.activiti.engine.impl.RepositoryServiceImpl;
import org.activiti.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.runtime.ProcessInstanceQuery;
import org.activiti.engine.task.Task;
import org.activiti.image.impl.DefaultProcessDiagramGenerator;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import boot.spring.pagemodel.Process;
import boot.spring.pagemodel.DataGrid;
import boot.spring.pagemodel.HistoryProcess;
import boot.spring.pagemodel.LeaveTask;
import boot.spring.pagemodel.MSG;
import boot.spring.pagemodel.RunningProcess;
import boot.spring.po.LeaveApply;
import boot.spring.po.Permission;
import boot.spring.po.Role;
import boot.spring.po.Role_permission;
import boot.spring.po.User;
import boot.spring.po.User_role;
import boot.spring.service.LeaveService;
import boot.spring.service.SystemService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

@Api(value = "请假流程接口")
@Controller
public class ActivitiController {

    private RepositoryService rep;
    private RuntimeService runService;
    private LeaveService leaveservice;
    private TaskService taskservice;
    private HistoryService historyService;
    private SystemService systemservice;

    public ActivitiController(RepositoryService rep, RuntimeService runService, LeaveService leaveservice, TaskService taskservice, HistoryService historyService, SystemService systemservice) {
        this.rep = rep;
        this.runService = runService;
        this.leaveservice = leaveservice;
        this.taskservice = taskservice;
        this.historyService = historyService;
        this.systemservice = systemservice;
    }

    @RequestMapping(value = "/processlist", method = RequestMethod.GET)
    String process() {
        return "activiti/processlist";
    }

    @RequestMapping(value = "/uploadworkflow", method = RequestMethod.POST)
    public String fileupload(
            @RequestParam MultipartFile uploadfile, HttpServletRequest request
    ) {
        try {
            MultipartFile file = uploadfile;
            String filename = file.getOriginalFilename();
            InputStream is = file.getInputStream();
            rep.createDeployment().addInputStream(filename, is).deploy();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "index";
    }

    @RequestMapping(value = "/getprocesslists", method = RequestMethod.POST)
    @ResponseBody
    public DataGrid<Process> getList(
            @RequestParam("current") int current,
            @RequestParam("rowCount") int rowCount
    ) {
        int firstRow = (current - 1) * rowCount;
        List<ProcessDefinition> list = rep.createProcessDefinitionQuery().listPage(firstRow, rowCount);
        int total = rep.createProcessDefinitionQuery().list().size();
        List<Process> myList = new ArrayList<Process>();
        for (int i = 0; i < list.size(); i++) {
            Process p = new Process();
            p.setDeploymentId(list.get(i).getDeploymentId());
            p.setId(list.get(i).getId());
            p.setKey(list.get(i).getKey());
            p.setName(list.get(i).getName());
            p.setResourceName(list.get(i).getResourceName());
            p.setDiagramresourcename(list.get(i).getDiagramResourceName());
            myList.add(p);
        }
        DataGrid<Process> grid = new DataGrid<Process>();
        grid.setCurrent(current);
        grid.setRowCount(rowCount);
        grid.setRows(myList);
        grid.setTotal(total);
        return grid;
    }

    @RequestMapping(value = "/showresource", method = RequestMethod.GET)
    public void export(
            @RequestParam("pdid") String pdid,
            @RequestParam("resource") String resource,
            HttpServletResponse response
    ) {
        ProcessDefinition def = rep.createProcessDefinitionQuery().processDefinitionId(pdid).singleResult();
        InputStream is = rep.getResourceAsStream(def.getDeploymentId(), resource);
        ServletOutputStream output = null;
        try {
            output = response.getOutputStream();
            IOUtils.copy(is, output);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @RequestMapping(value = "/deletedeploy", method = RequestMethod.POST)
    public String deleteDeploy(
            @RequestParam("deployid") String deployid
    ) {
        rep.deleteDeployment(deployid, true);
        return "activiti/processlist";
    }

    @RequestMapping(value = "/runningprocess", method = RequestMethod.GET)
    public String task() {
        return "activiti/runningprocess";
    }

    @RequestMapping(value = "/deptleaderaudit", method = RequestMethod.GET)
    public String mytask() {
        return "activiti/deptleaderaudit";
    }

    @RequestMapping(value = "/hraudit", method = RequestMethod.GET)
    public String hr() {
        return "activiti/hraudit";
    }

    @RequestMapping(value = "/index", method = RequestMethod.GET)
    public String my() {
        return "index";
    }

    @RequestMapping(value = "/leaveapply", method = RequestMethod.GET)
    public String leave() {
        return "activiti/leaveapply";
    }

    @RequestMapping(value = "/reportback", method = RequestMethod.GET)
    public String reprotback() {
        return "activiti/reportback";
    }

    @RequestMapping(value = "/modifyapply", method = RequestMethod.GET)
    public String modifyapply() {
        return "activiti/modifyapply";
    }

    @RequestMapping(value = "/startLeave", method = RequestMethod.POST)
    @ResponseBody
    public MSG startLeave(LeaveApply apply, HttpSession session) {
        String userid = (String) session.getAttribute("username");
        Map<String, Object> variables = new HashMap<String, Object>();
        variables.put("applyuserid", userid);
        ProcessInstance ins = leaveservice.startWorkflow(apply, userid, variables);
        System.out.println("流程id" + ins.getId() + "已启动");
        return new MSG("sucess");
    }

    @ApiOperation("获取部门领导审批代办列表")
    @RequestMapping(value = "/depttasklist", produces = {
            "application/json;charset=UTF-8"}, method = RequestMethod.POST)
    @ResponseBody
    public DataGrid<LeaveTask> getDeptTaskList(
            HttpSession session, @RequestParam("current") int current,
            @RequestParam("rowCount") int rowCount
    ) {
        DataGrid<LeaveTask> grid = new DataGrid<LeaveTask>();
        grid.setRowCount(rowCount);
        grid.setCurrent(current);
        grid.setTotal(0);
        grid.setRows(new ArrayList<LeaveTask>());
        // 先做权限检查，对于没有部门领导审批权限的用户,直接返回空
        String userId = (String) session.getAttribute("username");
        int uid = systemservice.getUidByusername(userId);
        User user = systemservice.getUserByid(uid);
        List<User_role> userRoles = user.getUser_roles();
        if (userRoles == null)
            return grid;
        boolean flag = false;// 默认没有权限
        for (int k = 0; k < userRoles.size(); k++) {
            User_role ur = userRoles.get(k);
            Role r = ur.getRole();
            int roleId = r.getRid();
            Role role = systemservice.getRolebyid(roleId);
            List<Role_permission> p = role.getRole_permission();
            for (int j = 0; j < p.size(); j++) {
                Role_permission rp = p.get(j);
                Permission permission = rp.getPermission();
                if (permission.getPermissionname().equals("部门领导审批"))
                    flag = true;
                else
                    continue;
            }
        }
        if (flag == false)// 无权限
        {
            return grid;
        } else {
            int firstRow = (current - 1) * rowCount;
            List<LeaveApply> results = leaveservice.getpagedepttask(userId, firstRow, rowCount);
            int totalSize = leaveservice.getalldepttask(userId);
            List<LeaveTask> tasks = new ArrayList<LeaveTask>();
            for (LeaveApply apply : results) {
                LeaveTask task = new LeaveTask();
                task.setApply_time(apply.getApply_time());
                task.setUser_id(apply.getUser_id());
                task.setEnd_time(apply.getEnd_time());
                task.setId(apply.getId());
                task.setLeave_type(apply.getLeave_type());
                task.setProcess_instance_id(apply.getProcess_instance_id());
                task.setProcessdefid(apply.getTask().getProcessDefinitionId());
                task.setReason(apply.getReason());
                task.setStart_time(apply.getStart_time());
                task.setTaskcreatetime(apply.getTask().getCreateTime());
                task.setTaskid(apply.getTask().getId());
                task.setTaskname(apply.getTask().getName());
                tasks.add(task);
            }
            grid.setRowCount(rowCount);
            grid.setCurrent(current);
            grid.setTotal(totalSize);
            grid.setRows(tasks);
            return grid;
        }
    }

    @RequestMapping(value = "/hrtasklist", produces = {"application/json;charset=UTF-8"}, method = RequestMethod.POST)
    @ResponseBody
    public DataGrid<LeaveTask> getHrTaskList(
            @RequestParam("current") int current,
            @RequestParam("rowCount") int rowCount,
            HttpSession session
    ) {
        DataGrid<LeaveTask> grid = new DataGrid<LeaveTask>();
        grid.setRowCount(rowCount);
        grid.setCurrent(current);
        grid.setTotal(0);
        grid.setRows(new ArrayList<LeaveTask>());
        // 先做权限检查，对于没有人事权限的用户,直接返回空
        String userId = (String) session.getAttribute("username");
        int uid = systemservice.getUidByusername(userId);
        User user = systemservice.getUserByid(uid);
        List<User_role> userRoles = user.getUser_roles();
        if (userRoles == null)
            return grid;
        boolean flag = false;// 默认没有权限
        for (int k = 0; k < userRoles.size(); k++) {
            User_role ur = userRoles.get(k);
            Role r = ur.getRole();
            int roleId = r.getRid();
            Role role = systemservice.getRolebyid(roleId);
            List<Role_permission> p = role.getRole_permission();
            for (int j = 0; j < p.size(); j++) {
                Role_permission rp = p.get(j);
                Permission permission = rp.getPermission();
                if (permission.getPermissionname().equals("人事审批"))
                    flag = true;
                else
                    continue;
            }
        }
        if (flag == false)// 无权限
        {
            return grid;
        } else {
            int firstRow = (current - 1) * rowCount;
            List<LeaveApply> results = leaveservice.getpagehrtask(userId, firstRow, rowCount);
            int totalsize = leaveservice.getallhrtask(userId);
            List<LeaveTask> tasks = new ArrayList<LeaveTask>();
            for (LeaveApply apply : results) {
                LeaveTask task = new LeaveTask();
                task.setApply_time(apply.getApply_time());
                task.setUser_id(apply.getUser_id());
                task.setEnd_time(apply.getEnd_time());
                task.setId(apply.getId());
                task.setLeave_type(apply.getLeave_type());
                task.setProcess_instance_id(apply.getProcess_instance_id());
                task.setProcessdefid(apply.getTask().getProcessDefinitionId());
                task.setReason(apply.getReason());
                task.setStart_time(apply.getStart_time());
                task.setTaskcreatetime(apply.getTask().getCreateTime());
                task.setTaskid(apply.getTask().getId());
                task.setTaskname(apply.getTask().getName());
                tasks.add(task);
            }
            grid.setRowCount(rowCount);
            grid.setCurrent(current);
            grid.setTotal(totalsize);
            grid.setRows(tasks);
            return grid;
        }
    }

    @RequestMapping(value = "/xjtasklist", produces = {"application/json;charset=UTF-8"}, method = RequestMethod.POST)
    @ResponseBody
    public DataGrid<LeaveTask> getXJtasklist(

            @RequestParam("current") int current,
            @RequestParam("rowCount") int rowCount,
            HttpSession session
    ) {
        int firstRow = (current - 1) * rowCount;
        String userId = (String) session.getAttribute("username");
        List<LeaveApply> results = leaveservice.getpageXJtask(userId, firstRow, rowCount);
        int totalsize = leaveservice.getallXJtask(userId);
        List<LeaveTask> tasks = new ArrayList<LeaveTask>();
        for (LeaveApply apply : results) {
            LeaveTask task = new LeaveTask();
            task.setApply_time(apply.getApply_time());
            task.setUser_id(apply.getUser_id());
            task.setEnd_time(apply.getEnd_time());
            task.setId(apply.getId());
            task.setLeave_type(apply.getLeave_type());
            task.setProcess_instance_id(apply.getProcess_instance_id());
            task.setProcessdefid(apply.getTask().getProcessDefinitionId());
            task.setReason(apply.getReason());
            task.setStart_time(apply.getStart_time());
            task.setTaskcreatetime(apply.getTask().getCreateTime());
            task.setTaskid(apply.getTask().getId());
            task.setTaskname(apply.getTask().getName());
            tasks.add(task);
        }
        DataGrid<LeaveTask> grid = new DataGrid<LeaveTask>();
        grid.setRowCount(rowCount);
        grid.setCurrent(current);
        grid.setTotal(totalsize);
        grid.setRows(tasks);
        return grid;
    }

    @RequestMapping(value = "/updatetasklist", produces = {"application/json;charset=UTF-8"}, method = RequestMethod.POST)
    @ResponseBody
    public DataGrid<LeaveTask> getUpdateTaskList(
            @RequestParam("current") int current,
            @RequestParam("rowCount") int rowCount,
            HttpSession session
    ) {
        int firstRow = (current - 1) * rowCount;
        String userId = (String) session.getAttribute("username");
        List<LeaveApply> results = leaveservice.getpageupdateapplytask(userId, firstRow, rowCount);
        int totalSize = leaveservice.getallupdateapplytask(userId);
        List<LeaveTask> tasks = new ArrayList<LeaveTask>();
        for (LeaveApply apply : results) {
            LeaveTask task = new LeaveTask();
            task.setApply_time(apply.getApply_time());
            task.setUser_id(apply.getUser_id());
            task.setEnd_time(apply.getEnd_time());
            task.setId(apply.getId());
            task.setLeave_type(apply.getLeave_type());
            task.setProcess_instance_id(apply.getProcess_instance_id());
            task.setProcessdefid(apply.getTask().getProcessDefinitionId());
            task.setReason(apply.getReason());
            task.setStart_time(apply.getStart_time());
            task.setTaskcreatetime(apply.getTask().getCreateTime());
            task.setTaskid(apply.getTask().getId());
            task.setTaskname(apply.getTask().getName());
            tasks.add(task);
        }
        DataGrid<LeaveTask> grid = new DataGrid<LeaveTask>();
        grid.setRowCount(rowCount);
        grid.setCurrent(current);
        grid.setTotal(totalSize);
        grid.setRows(tasks);
        return grid;
    }

    @RequestMapping(value = "/dealtask", method = RequestMethod.POST)
    @ResponseBody
    public LeaveApply taskDeal(
            @RequestParam("taskid") String taskid,
            HttpServletResponse response
    ) {
        Task task = taskservice.createTaskQuery().taskId(taskid).singleResult();
        ProcessInstance process = runService.createProcessInstanceQuery()
                .processInstanceId(task.getProcessInstanceId())
                .singleResult();
        LeaveApply leave = leaveservice.getleave(new Integer(process.getBusinessKey()));
        return leave;
    }

    @RequestMapping(value = "/activiti/task-deptleaderaudit", method = RequestMethod.GET)
    String url() {
        return "/activiti/task-deptleaderaudit";
    }

    @RequestMapping(value = "/task/deptcomplete/{taskid}", method = RequestMethod.POST)
    @ResponseBody
    public MSG deptComplete(
            @PathVariable("taskid") String taskid,
            HttpSession session,
            HttpServletRequest req
    ) {
        String userId = (String) session.getAttribute("username");
        Map<String, Object> variables = new HashMap<String, Object>();
        String approve = req.getParameter("deptleaderapprove");
        variables.put("deptleaderapprove", approve);
        taskservice.claim(taskid, userId);
        taskservice.complete(taskid, variables);
        return new MSG("success");
    }

    @RequestMapping(value = "/task/hrcomplete/{taskid}", method = RequestMethod.POST)
    @ResponseBody
    public MSG hrcomplete(
            @PathVariable("taskid") String taskid,
            HttpSession session,
            HttpServletRequest req
    ) {
        String userId = (String) session.getAttribute("username");
        Map<String, Object> variables = new HashMap<String, Object>();
        String approve = req.getParameter("hrapprove");
        variables.put("hrapprove", approve);
        taskservice.claim(taskid, userId);
        taskservice.complete(taskid, variables);
        return new MSG("success");
    }

    @RequestMapping(value = "/task/reportcomplete/{taskid}", method = RequestMethod.POST)
    @ResponseBody
    public MSG reportBackComplete(
            @PathVariable("taskid") String taskid,
            HttpServletRequest req
    ) {
        String realStartTime = req.getParameter("realstart_time");
        String realEndTime = req.getParameter("realend_time");
        leaveservice.completereportback(taskid, realStartTime, realEndTime);
        return new MSG("success");
    }

    @RequestMapping(value = "/task/updatecomplete/{taskid}", method = RequestMethod.POST)
    @ResponseBody
    public MSG updatecomplete(
            @PathVariable("taskid") String taskid,
            @ModelAttribute("leave") LeaveApply leave,
            @RequestParam("reapply") String reapply
    ) {
        leaveservice.updatecomplete(taskid, leave, reapply);
        return new MSG("success");
    }

    @RequestMapping(value = "involvedprocess", method = RequestMethod.POST) // 参与的正在运行的请假流程
    @ResponseBody
    public DataGrid<RunningProcess> allExecution(
            HttpSession session, @RequestParam("current") int current,
            @RequestParam("rowCount") int rowCount
    ) {
        int firstRow = (current - 1) * rowCount;
        String userId = (String) session.getAttribute("username");
        ProcessInstanceQuery query = runService.createProcessInstanceQuery();
        int total = (int) query.count();
        List<ProcessInstance> a = query.processDefinitionKey("leave").involvedUser(userId).listPage(firstRow, rowCount);
        List<RunningProcess> list = new ArrayList<RunningProcess>();
        for (ProcessInstance p : a) {
            RunningProcess process = new RunningProcess();
            process.setActivityid(p.getActivityId());
            process.setBusinesskey(p.getBusinessKey());
            process.setExecutionid(p.getId());
            process.setProcessInstanceid(p.getProcessInstanceId());
            list.add(process);
        }
        DataGrid<RunningProcess> grid = new DataGrid<RunningProcess>();
        grid.setCurrent(current);
        grid.setRowCount(rowCount);
        grid.setTotal(total);
        grid.setRows(list);
        return grid;
    }

    @RequestMapping(value = "/getfinishprocess", method = RequestMethod.POST)
    @ResponseBody
    public DataGrid<HistoryProcess> getHistory(
            @RequestParam("current") int current,
            @RequestParam("rowCount") int rowCount,
            HttpSession session
    ) {
        String userId = (String) session.getAttribute("username");
        HistoricProcessInstanceQuery process = historyService.createHistoricProcessInstanceQuery()
                .processDefinitionKey("leave").startedBy(userId).finished();
        int total = (int) process.count();
        int firstRow = (current - 1) * rowCount;
        List<HistoricProcessInstance> info = process.listPage(firstRow, rowCount);
        List<HistoryProcess> list = new ArrayList<HistoryProcess>();
        for (HistoricProcessInstance history : info) {
            HistoryProcess his = new HistoryProcess();
            String businessKey = history.getBusinessKey();
            LeaveApply apply = leaveservice.getleave(Integer.parseInt(businessKey));
            his.setLeaveapply(apply);
            his.setBusinessKey(businessKey);
            his.setProcessDefinitionId(history.getProcessDefinitionId());
            list.add(his);
        }
        DataGrid<HistoryProcess> grid = new DataGrid<HistoryProcess>();
        grid.setCurrent(current);
        grid.setRowCount(rowCount);
        grid.setTotal(total);
        grid.setRows(list);
        return grid;
    }

    @RequestMapping(value = "/historyprocess", method = RequestMethod.GET)
    public String history() {
        return "activiti/historyprocess";
    }

    @RequestMapping(value = "/processinfo", method = RequestMethod.POST)
    @ResponseBody
    public List<HistoricActivityInstance> processInfo(
            @RequestParam("instanceid") String instanceid
    ) {
        List<HistoricActivityInstance> his = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(instanceid).orderByHistoricActivityInstanceStartTime().asc().list();
        return his;
    }

    @RequestMapping(value = "/processhis", method = RequestMethod.POST)
    @ResponseBody
    public List<HistoricActivityInstance> processHis(
            @RequestParam("ywh") String ywh
    ) {
        String instanceId = historyService.createHistoricProcessInstanceQuery()
                .processDefinitionKey("purchase")
                .processInstanceBusinessKey(ywh).singleResult().getId();
        List<HistoricActivityInstance> his = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(instanceId).orderByHistoricActivityInstanceStartTime().asc().list();
        return his;
    }

    @RequestMapping(value = "myleaveprocess", method = RequestMethod.GET)
    String myleaveprocess() {
        return "activiti/myleaveprocess";
    }

    @RequestMapping(value = "traceprocess/{executionid}", method = RequestMethod.GET)
    public void traceprocess(
            @PathVariable("executionid") String executionid,
            HttpServletResponse response
    ) {
        ProcessInstance process = runService.createProcessInstanceQuery().processInstanceId(executionid).singleResult();
        BpmnModel bpmnmodel = rep.getBpmnModel(process.getProcessDefinitionId());
        List<String> activeActivityIds = runService.getActiveActivityIds(executionid);
        DefaultProcessDiagramGenerator gen = new DefaultProcessDiagramGenerator();
        // 获得历史活动记录实体（通过启动时间正序排序，不然有的线可以绘制不出来）
        List<HistoricActivityInstance> historicActivityInstances = historyService.createHistoricActivityInstanceQuery()
                .executionId(executionid).orderByHistoricActivityInstanceStartTime().asc().list();
        // 计算活动线
        List<String> highLightedFlows = leaveservice
                .getHighLightedFlows(
                        (ProcessDefinitionEntity) ((RepositoryServiceImpl) rep)
                                .getDeployedProcessDefinition(process.getProcessDefinitionId()),
                        historicActivityInstances);

        InputStream in = gen.generateDiagram(bpmnmodel, "png", activeActivityIds, highLightedFlows, "宋体", "宋体", null,
                null, 1.0);
        // InputStream in=gen.generateDiagram(bpmnmodel, "png",
        // activeActivityIds);
        ServletOutputStream output = null;
        try {
            output = response.getOutputStream();
            IOUtils.copy(in, output);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @RequestMapping(value = "myleaves", method = RequestMethod.GET)
    String myleaves() {
        return "activiti/myleaves";
    }

    @RequestMapping(value = "setupprocess", method = RequestMethod.POST)
    @ResponseBody
    public DataGrid<RunningProcess> setupProcess(
            @RequestParam("current") int current,
            @RequestParam("rowCount") int rowCount,
            HttpSession session
    ) {
        int firstRow = (current - 1) * rowCount;
        String userId = (String) session.getAttribute("username");
        System.out.print(userId);
        ProcessInstanceQuery query = runService.createProcessInstanceQuery();
        int total = (int) query.count();
        List<ProcessInstance> a = query.processDefinitionKey("leave").involvedUser(userId).listPage(firstRow, rowCount);
        List<RunningProcess> list = new ArrayList<RunningProcess>();
        for (ProcessInstance p : a) {
            RunningProcess process = new RunningProcess();
            process.setActivityid(p.getActivityId());
            process.setBusinesskey(p.getBusinessKey());
            process.setExecutionid(p.getId());
            process.setProcessInstanceid(p.getProcessInstanceId());
            LeaveApply l = leaveservice.getleave(Integer.parseInt(p.getBusinessKey()));
            if (l.getUser_id().equals(userId))
                list.add(process);
            else
                continue;
        }
        DataGrid<RunningProcess> grid = new DataGrid<RunningProcess>();
        grid.setCurrent(current);
        grid.setRowCount(rowCount);
        grid.setTotal(total);
        grid.setRows(list);
        return grid;
    }
}
