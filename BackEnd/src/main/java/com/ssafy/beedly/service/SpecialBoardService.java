package com.ssafy.beedly.service;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.ssafy.beedly.common.exception.NotFoundException;
import com.ssafy.beedly.common.exception.NotMatchException;
import com.ssafy.beedly.domain.Category;
import com.ssafy.beedly.domain.SpecialBoard;
import com.ssafy.beedly.domain.User;
import com.ssafy.beedly.dto.special.board.request.CreateSpecialBoardRequest;
import com.ssafy.beedly.dto.special.board.response.SpecialBoardResponse;
import com.ssafy.beedly.dto.special.board.response.SpecialBoardSimpleResponse;
import com.ssafy.beedly.repository.CategoryRepository;
import com.ssafy.beedly.repository.SpecialBoardRepository;
import com.ssafy.beedly.repository.query.SpecialBoardQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.ssafy.beedly.common.exception.NotFoundException.CATEGORY_NOT_FOUND;
import static com.ssafy.beedly.common.exception.NotMatchException.CONTENT_TYPE_NOT_MATCH;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpecialBoardService {

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    private final SpecialBoardRepository specialBoardRepository;
    private final SpecialBoardQueryRepository specialBoardQueryRepository;
    private final AmazonS3Client amazonS3Client;

    // 게시글 생성
    @Transactional
    public void createPost(User user, CreateSpecialBoardRequest request, MultipartFile image) {
        String imageUrl = uploadImageS3(image);
        specialBoardRepository.save(SpecialBoard.createSpecialBoard(user, request, imageUrl));
    }

    // 게시글 상세조회
    public SpecialBoardResponse searchSpecialBoard(Long boardId) {
         return new SpecialBoardResponse(specialBoardQueryRepository.findByIdWithProducts(boardId));
    }

    // 진행 예정인 게시글 리스트 조회
    public List<SpecialBoardSimpleResponse> searchWaitingSpecialBoards() {
        return specialBoardRepository.findWaitingSpecialBoardOrderByStartTimeAsc(LocalDateTime.now())
                .stream().map(specialBoard -> new SpecialBoardSimpleResponse(specialBoard))
                .collect(Collectors.toList());
    }

    public String uploadImageS3(MultipartFile image) {
        String imageUrl = null;

        if(image != null) {
            if (!image.getContentType().startsWith("image")) {
                throw new NotMatchException(CONTENT_TYPE_NOT_MATCH);
            }

            ObjectMetadata objectMetadata = new ObjectMetadata();
            objectMetadata.setContentLength(image.getSize());
            objectMetadata.setContentType(image.getContentType());
            String storeName = UUID.randomUUID().toString();

            try {
                amazonS3Client.putObject((new PutObjectRequest(bucket, storeName, image.getInputStream(), objectMetadata)
                        .withCannedAcl(CannedAccessControlList.PublicRead)));

                // 이미지 url 가져오기
                imageUrl = amazonS3Client.getUrl(bucket, storeName).toString();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return imageUrl;
    }

}
