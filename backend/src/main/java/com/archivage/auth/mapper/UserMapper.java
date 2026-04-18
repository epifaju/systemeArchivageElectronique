package com.archivage.auth.mapper;

import com.archivage.auth.dto.UserSummaryDto;
import com.archivage.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserMapper {

    UserSummaryDto toSummary(User user);
}
