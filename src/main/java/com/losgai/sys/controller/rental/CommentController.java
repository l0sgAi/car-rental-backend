package com.losgai.sys.controller.rental;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.losgai.sys.common.sys.Result;
import com.losgai.sys.entity.carRental.Comment;
import com.losgai.sys.enums.ResultCodeEnum;
import com.losgai.sys.service.rental.CommentService;
import com.losgai.sys.vo.CommentVo;
import com.losgai.sys.vo.TopCommentVo;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Objects;

@RestController
@RequiredArgsConstructor
@RequestMapping("/rental/comment")
@Slf4j
public class CommentController {

    private final CommentService commentService;

    @SaCheckRole("admin")
    @PostMapping("/admin/add")
    @Tag(name = "新增评论",description = "管理员新增/回复评论")
    public Result<String> add(@RequestBody @Valid Comment comment) {
        Long userId = StpUtil.getLoginIdAsLong();
        ResultCodeEnum codeEnum = commentService.add(comment,userId);
        if (!Objects.equals(codeEnum.getCode(), ResultCodeEnum.SUCCESS.getCode())) {
            return Result.info(codeEnum.getCode(),codeEnum.getMessage());
        }
        return Result.success("添加成功");
    }

    @PostMapping("/user/add")
    @Tag(name = "新增评论",description = "用户新增/回复评论")
    public Result<String> userAdd(@RequestBody @Valid Comment comment) {
        Long userId = StpUtil.getLoginIdAsLong();
        ResultCodeEnum codeEnum = commentService.userAdd(comment, userId);
        if (!Objects.equals(codeEnum.getCode(), ResultCodeEnum.SUCCESS.getCode())) {
            return Result.info(codeEnum.getCode(),codeEnum.getMessage());
        }
        return Result.success("添加成功");
    }

    @SaCheckRole("admin")
    @PutMapping("/admin/delete")
    @Tag(name = "管理员删除评论",description = "删除评论")
    public Result<String> delete(@RequestParam Long id) {
        ResultCodeEnum codeEnum = commentService.delete(id);
        if (!Objects.equals(codeEnum.getCode(), ResultCodeEnum.SUCCESS.getCode())) {
            return Result.info(codeEnum.getCode(),codeEnum.getMessage());
        }
        return Result.success("删除成功");
    }

    @SaCheckRole("admin")
    @GetMapping("/admin/list")
    @Tag(name = "获取所有评论信息", description = "管理员分页获取当前所有评论信息列表")
    public Result<List<TopCommentVo>> query(
            @RequestParam(required = false) String keyWord,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        // 开启分页
        PageHelper.startPage(pageNum, pageSize);
        // 执行查询
        List<TopCommentVo> list = commentService.query(keyWord);
        // 获取分页信息
        PageInfo<TopCommentVo> pageInfo = new PageInfo<>(list);
        // 使用自定义分页返回方法
        return Result.page(list, pageInfo.getTotal());
    }

    @GetMapping("/user/list")
    @Tag(name = "获取车辆评论信息", description = "用户获取当前车辆初始评论信息列表")
    public Result<List<TopCommentVo>> list(@RequestParam Long carId) {
        // 执行查询
        List<TopCommentVo> list = commentService.queryByCarId(carId);
        return Result.success(list);
    }

    @GetMapping("/user/moreComment")
    @Tag(name = "获取车辆评论信息", description = "用户获取更多评论信息")
    public Result<List<TopCommentVo>> moreComment(
            @RequestParam Long carId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        // 开启分页
        PageHelper.startPage(pageNum, pageSize);
        // 执行查询
        List<TopCommentVo> list = commentService.getMore(carId);
        // 获取分页信息
        PageInfo<TopCommentVo> pageInfo = new PageInfo<>(list);
        // 使用自定义分页返回方法
        return Result.page(list, pageInfo.getTotal());
    }

    @GetMapping("/user/loadReply")
    @Tag(name = "获取车辆评论回复信息", description = "用户根据顶级评论id，分页获取当前评论回复列表")
    public Result<List<CommentVo>> loadReply(
            @RequestParam Long id,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "5") int pageSize) {
        // 开启分页
        PageHelper.startPage(pageNum, pageSize);
        // 执行查询
        List<CommentVo> list = commentService.loadReplyByCommentId(id);
        // 获取分页信息
        PageInfo<CommentVo> pageInfo = new PageInfo<>(list);
        // 使用自定义分页返回方法
        return Result.page(list, pageInfo.getTotal());
    }

    @PostMapping("/user/like")
    @Tag(name = "用户点赞", description = "用户点赞")
    public Result<String> like(@RequestParam Long commentId) {
        Long userId = StpUtil.getLoginIdAsLong();
        ResultCodeEnum codeEnum = commentService.like(commentId, userId);
        if (!Objects.equals(codeEnum.getCode(), ResultCodeEnum.SUCCESS.getCode())) {
            return Result.info(codeEnum.getCode(),codeEnum.getMessage());
        }
        return Result.success("点赞成功");
    }

}
