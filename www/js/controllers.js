angular.module('starter.controllers', [])

.controller('DashCtrl', function($scope) {

})

.controller('ChatsCtrl', function($scope, Chats) {
  $scope.chats = Chats.all();
  $scope.remove = function(chat) {
    Chats.remove(chat);
  }
  $scope.getGroups = function () {
    easemob.getGroups(function (argument) {
      alert(argument);
      console.log(argument);
      $scope.groups = argument;
      $scope.$digest();
    },function (argument) {
      alert(argument);
      console.log(argument);
    },[true])
  } 
  $scope.getContacts = function () {
    easemob.getContacts(function (argument) {
      alert(argument);
      console.log(argument);
      $scope.contacts = argument;
      $scope.$digest();
    },function (argument) {
      alert(argument);
      console.log(argument);
    })
  } 
  $scope.logout = function (argument) {
    easemob.logout(function (argument) {
      alert(argument);
      console.log(argument);
    },function (argument) {
      alert(argument);
      console.log(argument);
    })
  }
})

.controller('ChatDetailCtrl', function($scope, $stateParams, Chats) {
  $scope.chat = Chats.get($stateParams.chatId);
  $scope.recordStart = false;
  $scope.content = {
    text:'',
    user:'',
    type:''
  }
  $scope.resend= function () {
    easemob.chat(function (chat) {
      $scope.chat = chat;
      console.log(chat);
      $scope.$digest();
    },function (chat) {
      $scope.chat = chat;
      $scope.$digest();
    },[{
      target:$scope.content.user,
      resend:true,
      msgId:$scope.content.text
    }])
  } 
  $scope.send= function () {
    easemob.chat(function (chat) {
      $scope.chat = chat;
      console.log(chat);
      $scope.$digest();
    },function (chat) {
      $scope.chat = chat;
      $scope.$digest();
    },[{
      chatType:$scope.content.type,
      target:$scope.content.user,
      contentType:'TXT',
      content:{'text':$scope.content.text},
      extend:{a:1}
    }])
  } 
  $scope.clearMessages = function () {
    $scope.chats =[];
  }
  $scope.getMessages= function () {
    var param = [$scope.content.type,$scope.content.user];
    if($scope.msgId){
      param.push($scope.msgId);
    }
    easemob.getMessages(function (chats) {
      $scope.chats = $scope.chats ? chats.concat($scope.chats):chats;
      $scope.msgId = chats.length>0 ?chats[0].msgId :undefined;
      $scope.$digest();
    },function (argument) {
      alert(argument);
      console.log(argument);
    },param);
  } 
  //
  var my_media = null;
  var mediaTimer = null;
  function playAudio(body) {
    var url = body.localUrl ?('file://'+body.localUrl):body.remoteUrl;
    // Create Media object from src
    my_media = new Media(url, mediaOnSuccess, mediaOnError);
    // Play audio
    my_media.play();
    // Update my_media position every second
    if (mediaTimer != null) {
      mediaTimer =null;
    }
    mediaTimer = setInterval(function() {
          // get my_media position
          my_media.getCurrentPosition(
            // success callback
            function(position) {
              if (position > -1) {
                  body.position = position;
              }
              else{
                body.play =false;
              }
            },
            // error callback
            function(e) {
              console.log("Error getting pos=" + e);
              body.position = 0;
            }
          );
      }, 1000);
  }
  // onSuccess Callback
  //
  function mediaOnSuccess() {
    console.log("playAudio():Audio Success");
    my_media.stop();
    my_media.release();
  }

  // onError Callback
  //
  function mediaOnError(error) {
    console.log('code: '    + error.code    + '\n' +
      'message: ' + error.message + '\n');
    my_media.stop();
    my_media.release();
  }
  // Pause audio
  //
  function pauseAudio() {
    if (my_media) {
      my_media.pause();
    }
  }

  // Stop audio
  //
  function stopAudio() {
    if (my_media) {
      my_media.stop();
      my_media.release();
    }
    clearInterval(mediaTimer);
    mediaTimer = null;
  }

  $scope.toggleVoice = function (body) {
    if(!body.play){
      playAudio(body);
      body.isListened =true;
    }
    else{
      stopAudio();
    }
    
  }
  easemob.onReciveMessage = function (chat) {
    $scope.chat = chat;
    $scope.$digest();
  }
  easemob.onClickNotification = function (chat) {
    console.log(chat);
  }
  
  easemob.onRecord = function (msg) {
    $scope.what = msg.what;
    $scope.$digest();
  }

  $scope.captureAudio = function () {
    $scope.recordStart = true;
    easemob.recordStart([$scope.content.user])
  }
  $scope.stopCaptureAudio = function () {
    $scope.recordStart = false;
    easemob.recordEnd(function (argument) {
      alert(argument);
      console.log(argument);
    },function (argument) {
      alert(argument);
      console.log(argument);
    },[$scope.content.type, $scope.content.user])
  }


  function ImageOnSuccess(imageURI) {
    if(imageURI instanceof Array) {
      imageURI = imageURI[0];
    }
    console.log(imageURI);
    easemob.chat(function (argument) {
      alert(argument);
      console.log(argument);
    },function (argument) {
      alert(argument);
      console.log(argument);
    },[{
      chatType:$scope.content.type,
      target:$scope.content.user,
      contentType:'IMAGE',
      content:{'filePath':imageURI}
    }]);
    // [$scope.content.type, $scope.content.user, 'IMAGE', {'filePath':imageURI}])
  }

  function ImageOnFail(message) {
    alert('Failed because: ' + message);
  }
  $scope.captureImage = function (source) {
    if(source === 'camera') {
      source = Camera.PictureSourceType.CAMERA;
      navigator.camera.getPicture(ImageOnSuccess, ImageOnFail, { quality: 50,
        destinationType: Camera.DestinationType.FILE_URI,sourceType: source });
      return;
    }
    window.imagePicker.getPictures(ImageOnSuccess, ImageOnFail, {
      maximumImagesCount: 1, width: 800
    });
  }
})

.controller('AccountCtrl', function($scope) {
  $scope.settings = {    
    enableFriends: true
  };
});
