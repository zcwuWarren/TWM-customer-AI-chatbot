/** 共用元件 > POP UP */
function popup() {
  console.log("popup");

  $(".btnPopup").click(function () {
    $(".overlay").fadeIn();
    $(".popup").addClass("active");
    $("body").addClass("fixed");
  });
  $(".popup .btn, .popup .btnClose").click(function () {
    $(".overlay").fadeOut();
    $(".popup").removeClass("active");
    $("body").removeClass("fixed");
  });
}
