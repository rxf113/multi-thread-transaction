package com.rxf113.multithreadtransaction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

public interface DemoMapper extends BaseMapper {

    @Insert("INSERT INTO rxf113.user (id, name, age, high, ud_id) VALUES (#{id}, #{name}, 12, '5.23', '3');")
    Integer insert(@Param("id") String id, @Param("name") String name);


}
