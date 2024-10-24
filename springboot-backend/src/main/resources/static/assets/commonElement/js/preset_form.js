/** 密碼欄位眼睛 */
function passwordEyes() {
  console.log("passwordEyes");
  $(".input.password .passwordInside").append('<div class="inputEyeBtn" />');

  $(".inputEyeBtn").on("mousedown touchstart", passwordOpenEyes);
  $(".inputEyeBtn").on("mouseup mouseout touchcancel touchend touchmove", passwordCloseEyes);
}

// 功能function

/** 密碼欄位眼睛 開 */
function passwordOpenEyes() {
  $(this).parent().find("input").prop("type", "text");
  $(this).parent().find(".inputEyeBtn").addClass("openEyes");
}

/** 密碼欄位眼睛 關 */
function passwordCloseEyes() {
  $(this).parent().find("input").prop("type", "password");
  $(this).parent().find(".inputEyeBtn").removeClass("openEyes");
}
