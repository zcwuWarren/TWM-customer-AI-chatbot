// chat.js

let stompClient = null;
const accessToken = localStorage.getItem('accessToken');  // 用戶的 JWT token
let chatSessionId = generateChatSessionId();
localStorage.setItem('chatSessionId', chatSessionId);

function connect() {
    let alertDisplayed = false;

    const socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);

    const headers = {
        'Authorization': 'Bearer ' + localStorage.getItem('accessToken')
    };

    stompClient.connect(headers, function(frame) {
        console.log('Connected: ' + frame);
        onConnected();
    }, function(error) {
        if (!alertDisplayed) {
            alertDisplayed = true;  // Set the flag to prevent multiple alerts
            console.error('Could not connect to WebSocket server:', error);
            // Clear the JWT token from localStorage
            localStorage.removeItem('accessToken');
            alert('請重新登錄。');
            // Redirect to login page
            window.location.href = '/account_login.html';
        }
    });
}

function onConnected() {
    stompClient.subscribe('/user/queue/reply/' + chatSessionId, onMessageReceived);
    console.log('Subscribed to /user/queue/reply/' + chatSessionId);

    // 訂閱 Redis Pub/Sub 頻道來接收來自代理的通知
    stompClient.subscribe('/topic/chatSessionSwitch', function(message) {
        console.log('Received message from chatSessionSwitch:', message.body);
        const switchChatSessionId = JSON.parse(message.body);
        if (switchChatSessionId === chatSessionId) {
            switchToAgentChannel(chatSessionId);
        }
    });

    // 構建要傳遞的 payload，包含 chatSessionId 和 jwtToken
    const payload = {
        type: 'CONNECT',
        chatSessionId: chatSessionId,
        jwtToken: localStorage.getItem('accessToken')
    };

    // 通知後端用戶已連線，傳遞 chatSessionId 和 jwtToken
    stompClient.send("/app/chat.connect", {}, JSON.stringify(payload));

    // 請求初始 FAQ
    stompClient.send("/app/chat.getInitialFAQ", {}, JSON.stringify({chatSessionId: chatSessionId}));
}

function switchToAgentChannel(switchChatSessionId) {
    console.log('Switching to agent channel for session:', switchChatSessionId);

    // 取消訂閱舊的頻道
    stompClient.unsubscribe('/user/queue/reply/' + chatSessionId);
    console.log('Unsubscribed from /user/queue/reply/' + chatSessionId);

    // 訂閱新的 agent 頻道
    stompClient.subscribe('/queue/agent/' + switchChatSessionId, onMessageReceived);
    console.log('Subscribed to /queue/agent/' + switchChatSessionId);

    // 顯示一條消息，告知用戶已切換到人工客服
    displayBotMessage("您已成功切換到人工客服，請稍候，客服人員即將為您服務。");
}

function onError(error) {
    console.log('Could not connect to WebSocket server. Please refresh this page to try again!');
}

function sendMessage(content) {
    let messageContent = content || $(".textareaBox textarea").val().trim();
    if (messageContent && accessToken && chatSessionId) {
        if (!stompClient) {
            connect();
        }
        const chatMessage = {
            chatSessionId: chatSessionId,  // 這裡的 sessionId 是 chatSessionId
            content: messageContent,
            sender: 'User',
            type: 'CHAT',
            token: accessToken  // JWT token
        };
        console.log('Sending message:', chatMessage);
        stompClient.send("/app/chat.sendMessage", {}, JSON.stringify(chatMessage));
        displayUserMessage(messageContent);
        showLoadingStatus();
        $(".textareaBox textarea").val('');  // 清空輸入框
        floatBoxClose();  // 確保 floatBox 在發送消息時關閉
    } else {
        console.error('Missing required identifiers: accessToken or chatSessionId');
    }
}

function generateChatSessionId() {
    return Date.now() + '_' + Math.random().toString(36).slice(2, 11);
}

function onMessageReceived(payload) {
    console.log('Received message:', payload);  // 檢查接收到的訊息
    const message = JSON.parse(payload.body);
    hideLoadingStatus();
    // 如果訊息的發送者是當前用戶，則不重複顯示
    if (message.sender === 'User') {
        console.log('Ignoring message sent by current user:', message.content);
        return;
    }

    if (message.type === 'HUMAN_SUPPORT_SUGGESTION') {
        displayBotMessage(message.content);
        displayHumanSupportButton();
    } else if (message.type === 'FAQ_SUGGESTIONS') {
        console.log('FAQ Suggestions:', message.content);
        displayFaqSuggestions(message.content.split('\n'));
    } else if (message.type === 'SUGGESTIONS') {
        displayInputSuggestions(message.content.split('\n'));
    } else {
        displayBotMessage(message.content);
    }
}

function displayInputSuggestions(suggestions) {
    const suggestionElement = $("#suggestions");  // 定位到 `#suggestions` 區域
    suggestionElement.empty();  // 清空當前內容

    const filteredSuggestions = suggestions.filter(suggestion => suggestion.trim() !== "");  // 過濾掉空的建議

    if (filteredSuggestions.length > 0) {
        // 隱藏隨機問題區域
        $(".float").hide();

        filteredSuggestions.forEach(function(suggestion) {
            suggestionElement.append("<div class='suggestion-item'>" + suggestion + "</div>");
        });
        suggestionElement.show();  // 顯示建議區域
    } else {
        suggestionElement.hide();  // 隱藏建議區域

        // 如果對話還沒開始，顯示隨機問題區域
        if (!$(".chat .msgRight").length) {
            $(".float").show();
        }
    }

    // 為每個建議項添加點擊事件
    $(".suggestion-item").on("click", function() {
        const selectedQuestion = $(this).text();
        sendMessage(selectedQuestion);  // 點擊後發送該問題
        $("#suggestions").hide();  // 點擊後隱藏建議區域

        // 移除隨機問題區域（對話已經開始）
        $(".float").remove();
    });
}

function displayFaqSuggestions(suggestions) {
    const suggestionElement = $(".float div");  // 定位到 `float` 區域內的 div
    suggestionElement.empty();  // 清空當前內容

    suggestions.forEach(function(suggestion) {
        suggestionElement.append("<button class='btn'>" + suggestion + "</button>");
    });

    // 為每個按鈕添加點擊事件
    $(".float .btn").on("click", function() {
        const selectedQuestion = $(this).text();
        sendMessage(selectedQuestion);  // 點擊後發送該問題
        floatBoxClose();  // 點擊後關閉 `floatBox`
    });

    scrollDown();
}

function displayUserMessage(message) {
    const messageElement = $("<li class='msg msgRight'><div class='dialog'><div class='content'>" + message + "</div><time>" + getCurrentTime() + "</time></div></li>");
    $(".chat").append(messageElement);
    scrollDown();
}

function displayBotMessage(message) {
    const messageElement = $("<li class='msg'><i class='avatar'></i><div class='dialog'><div class='content'>" + message + "</div><time>" + getCurrentTime() + "</time></div></li>");
    $(".chat").append(messageElement);
    scrollDown();
}

function getCurrentTime() {
    const now = new Date();
    return now.getHours().toString().padStart(2, '0') + ":" + now.getMinutes().toString().padStart(2, '0');
}

function floatBoxClose() {
    $(".float").slideUp(600);
}

function scrollDown() {
    $("main").scrollTop($("main").prop("scrollHeight"));
}

function debounce(func, delay) {
    let timer;
    return function(...args) {
        clearTimeout(timer);
        timer = setTimeout(() => {
            func.apply(this, args);
        }, delay);
    };
}
function displayHumanSupportButton() {
    const buttonElement = $("<button class='btn humanSupportBtn'>轉接人工客服</button>");
    $(".chat").append(buttonElement);
    buttonElement.click(requestHumanSupport);
    scrollDown();
}

// Function to show the loading status in the chat window
function showLoadingStatus() {
    const loadingElement = $('<li class="loading-status"><i class="avatar"></i><div class="dialog"><div class="content">請稍等回覆...</div></div></li>');
    $(".chat").append(loadingElement);
    scrollDown();  
}

// Function to hide the loading status once the response is received
function hideLoadingStatus() {
    $(".loading-status").remove(); 
    scrollDown(); 
}

function requestHumanSupport() {
    if (stompClient) {
        const chatMessage = {
            chatSessionId: chatSessionId,
            content: "REQUEST_HUMAN_SUPPORT",
            sender: 'User',
            type: 'CHAT',
            token: accessToken
        };
        stompClient.send("/app/chat.requestHumanSupport", {}, JSON.stringify(chatMessage));
    }
}
function loadChatHistory(baseUrl) {
    const apiUrl = `${baseUrl}/api/chat-history/latest-messages`;

    fetch(apiUrl, {
        method: 'GET',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': 'Bearer ' + localStorage.getItem('accessToken')
        }
    })
        .then(response => response.json())
        .then(data => {
            if (data && data.data) {
                data.data.forEach(message => {
                    if (message.sender === 'User') {
                        displayUserMessage(message.content);
                    } else if (message.sender === 'Bot' || 'agent') {
                        displayBotMessage(message.content);
                    }
                });
                console.log('Chat history loaded successfully');
            } else {
                console.log('No chat history found');
            }
        })
        .catch(error => {
            console.error('Error loading chat history:', error);
        });
}


$(document).ready(function () {
    // 預設 baseUrl
    let baseUrl = 'http://localhost:8080';  // 預設值

    // 動態獲取 baseUrl
    fetch('/api/config/base-url')
        .then(response => response.text())
        .then(fetchedUrl => {
            baseUrl = fetchedUrl;

            // 在獲取到 baseUrl 後進行其他初始化
            connect();


            let isComposing = false;

            $(".textareaBox textarea").on('compositionstart', function () {
                isComposing = true;
            });

            $(".textareaBox textarea").on('compositionend', function () {
                isComposing = false;
            });

            $(".textareaBox textarea").on('keydown', function (event) {
                if (event.key === "Enter" && !event.shiftKey && !isComposing) {
                    event.preventDefault();
                    // Hide the preset buttons container
                    $("#presetButtonsContainer").removeClass('d-flex').addClass('d-none');
                    sendMessage();
                }
            });


            $(".textareaBox button").click(function(event) {
                event.preventDefault();
                sendMessage();
            });

            // Event listener for loading chat history
            $('#loadHistory').on('click', function (event) {
                event.preventDefault();
                loadChatHistory(baseUrl);  // 傳入 baseUrl
                $(this).closest('.msg').hide();
                scrollDown();
            });


            // Event listener for skipping chat history
            $('#skipHistory').on('click', function (event) {
                event.preventDefault();
                $(this).closest('.msg').hide();
                displayBotMessage("您好，請問您需要什麼協助？");
            });

            $(".textareaBox button").click(function(event) {
                event.preventDefault();
                sendMessage();
            });

            $(".textareaBox textarea").on('input', debounce(function() {
                const userInput = $(this).val().trim();
                if (userInput && stompClient) {
                    stompClient.send("/app/chat.getSuggestions", {}, JSON.stringify({
                        chatSessionId: chatSessionId,
                        content: userInput,
                        type: 'SUGGESTIONS'
                    }));
                } else {
                    $("#suggestions").hide();
                }
            }, 300));

            scrollDown();
        })
        .catch(error => {
            console.error('Error fetching base URL:', error);
            // 使用預設的 baseUrl 進行初始化
            connect();
        });
});

function saveChatSession(chatSessionId) {
    fetch('/api/chat-history/save?chatSessionId='+chatSessionId, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': 'Bearer ' + accessToken
        },
    })
    .then(response => response.json())
    .then(data => {
        console.log('chat history saved', data);
    })
    .catch(error => {
        console.error('error saving chat history', error);
    });
}

window.addEventListener('beforeunload', function(event) {
    event.preventDefault();
    saveChatSession(chatSessionId);
    event.returnValue = confirmationMessage;
});




