import io
import re
import os
import six
import base64
import face_recognition
import numpy as np
import urllib.request
import json
from azure.cognitiveservices.vision.face import FaceClient
from msrest.authentication import CognitiveServicesCredentials
from azure.cognitiveservices.vision.computervision import ComputerVisionClient
from azure.cognitiveservices.vision.customvision.prediction import CustomVisionPredictionClient
from msrest.authentication import ApiKeyCredentials
from google.cloud import vision
from google.cloud import translate_v2 as translate
from flask import Flask, request, abort
import logging

app = Flask(__name__)
app.logger.setLevel(logging.DEBUG)

API_KEY = "070faf614b8844f1beabc0f55ae854ec"
ENDPOINT = "https://mohamedhesham.cognitiveservices.azure.com/"
cv_client = ComputerVisionClient(ENDPOINT, CognitiveServicesCredentials(API_KEY))

API_KEY_FACE = 'b3248e9827f943fca4a9966fddbe836f'
ENDPOINT_FACE = 'https://myfaceapi2.cognitiveservices.azure.com/'
face_client = FaceClient(ENDPOINT_FACE, CognitiveServicesCredentials(API_KEY_FACE))

os.environ['GOOGLE_APPLICATION_CREDENTIALS'] = r'ServiceAccountToken.json'

TOLERANCE = 0.59
MODEL = 'hog'


def ImageCaptioning(img):
    try:
        result = ""
        max_description = 3
        response = cv_client.describe_image_in_stream(img, max_description)
        for caption in response.captions:
            print('Image Description: {0}'.format(caption.text))
            result = 'I can see {0}'.format(caption.text)
            print('Confidence {0}'.format(caption.confidence * 100))
        return result
    except:
        return "There was an Error while Scanning"


def ObjectDetection(img):
    try:
        result = ""
        counter = 0
        detect_object = cv_client.detect_objects_in_stream(img)
        if (len(detect_object.objects) == 0):
            print("No objects detected.")
            result = "I cannot recognize any object "
            return result
        else:
            print(len(detect_object.objects))
            result = "I can recognize "
            for object in detect_object.objects:
                print("'{}' with confidence {:.2f}%".format(object.object_property, object.confidence * 100))
                if (counter != len(detect_object.objects) - 1):
                    result += "'{}', ".format(object.object_property)
                else:
                    result += " and '{}' ".format(object.object_property)
                counter += 1
            result = re.sub(r'[\']', "", result)
            return result
    except:
        return "There was an Error while Detecting"


def FaceDetection(img):
    try:
        response_detected_faces = face_client.face.detect_with_stream(
            img,
            detection_model='detection_01',
            recognition_model='recognition_04',
            return_face_attributes=['emotion', 'gender', 'age'],

        )

        print(response_detected_faces)

        if not response_detected_faces:
            raise Exception('No face detected')

        print('Number of people detected: {0}'.format(len(response_detected_faces)))

        counter = 0
        result = ""
        for face in response_detected_faces:
            gender = face.face_attributes.gender
            emotion = face.face_attributes.emotion
            age = face.face_attributes.age
            neutral = '{0:.0f}%'.format(emotion.neutral * 100)
            happiness = '{0:.0f}%'.format(emotion.happiness * 100)
            anger = '{0:.0f}%'.format(emotion.anger * 100)
            sandness = '{0:.0f}%'.format(emotion.sadness * 100)
            surprised = '{0:.0f}%'.format(emotion.surprise * 100)
            fear = '{0:.0f}%'.format(emotion.fear * 100)

            print("person " + str(counter))
            print("Gender: " + gender)
            print("Age: " + str(int(age)))
            print("neutral: " + neutral)
            print("Happy: " + happiness)
            print("Angery: " + anger)
            print("Sad: " + sandness)
            print("surprised: " + surprised)
            print("fear: " + fear)

            list = {'Neutral': emotion.neutral, 'Happy': emotion.happiness, 'Angry': emotion.anger,
                    'Sad': emotion.sadness,
                    'Surprised': emotion.surprise, 'Fear': emotion.fear}

            final_emotion = max(list, key=list.get)
            print("Emotion of this person is " + final_emotion)
            counter += 1
            result += "Person " + str(counter) + "\n" +  "Gender: " + gender + "\n" + "Age: " + str(
                int(age)) + "\n" + "Emotion of this person is " + final_emotion + "\n"
        return result
    except:
        return "There was an Error while Detecting"


def detect_labels(img):
    try:
        counter = 0
        result = "I can recognize "
        client = vision.ImageAnnotatorClient()

        image = vision.Image(content=img)

        response = client.label_detection(image=image)
        labels = response.label_annotations

        if (len(labels) == 0):
            print("No objects detected.")
            result = "I cannot recognize any label "
            return result
        else:
            for label in labels:
                if (counter != len(labels) - 1):
                    result += label.description + ", "
                else:
                    result += " and " + label.description
                counter += 1
            return result
    except:
        return "There was an Error while Detecting"


def detect_document(img):
    try:
        client = vision.ImageAnnotatorClient()
        image = vision.Image(content=img)
        response = client.document_text_detection(image=image)
        result = response.full_text_annotation.text
        if response.error.message:
            raise Exception(
                '{}\nFor more info on error messages, check: '
                'https://cloud.google.com/apis/design/errors'.format(
                    response.error.message))
        if(result == ''):
            return "There is no text in this Image"
        else:
            return result
    except:
        return "There was an Error while Scanning"


def translate_text(text, target):
    try:
        translate_client = translate.Client()

        if isinstance(text, six.binary_type):
            text = text.decode("utf-8")

        result = translate_client.translate(text, target_language=target)

        print(result)

        print(u"Text: {}".format(result["input"]))
        print(u"Translation: {}".format(result["translatedText"]))
        print(u"Detected source language: {}".format(result["detectedSourceLanguage"]))

        return result["translatedText"]
    except:
        return "There was an Error while Translating"


def Currency_Recognition(img):
    try:
        Prediction_Key = "a875eed7217c4bae8d68ac7dc8d5e1ef"
        endpoint = "https://southcentralus.api.cognitive.microsoft.com/"
        project_id = "8dd61d81-acfd-4e1f-a2e0-43371d952931"
        iteration_name = "Iteration2"
        Credentials = ApiKeyCredentials(in_headers={"Prediction-Key": Prediction_Key})
        predictor = CustomVisionPredictionClient(endpoint, Credentials)

        currnecy_dic = {}

        with io.BufferedReader(img) as image_file:
            results = predictor.classify_image_with_no_store(project_id, iteration_name, image_file.read()
                                                             )

            for prediction in results.predictions:
                currnecy_dic[prediction.tag_name] = round(prediction.probability, 2)
                # print("\t" + prediction.tag_name +": {0:.2f}%".format(prediction.probability * 100))

        predicted_currency = max(currnecy_dic, key=currnecy_dic.get)

        if(predicted_currency == "Not Currency"):
            return "There is no currency in this Image"
        else:
            return "I can recognize " + predicted_currency
    except:
        return "There was an Error while Detecting"


@app.route("/VisuallyImpaired", methods=['POST'])
def AI():
    # print(request.json)
    if not request.json or 'image' not in request.json:
        abort(400)

    # get the base64 encoded string
    im_b64 = request.json['image']

    # convert it into bytes
    img_bytes = base64.b64decode(im_b64.encode('utf-8'))

    img = io.BytesIO(img_bytes)

    choice = request.json['operation']

    if choice == "1":
        result = ImageCaptioning(img)
    elif choice == "2":
        result = ObjectDetection(img)
    elif choice == "3":
        result = FaceDetection(img)
    elif choice == "4":
        result = detect_document(img_bytes)
    elif choice == "5":
        result = Currency_Recognition(img)
    elif choice == "6":
        result = detect_labels(img_bytes)
    else:
        result = "No chosen operation"

    # access other keys of json
    # print(request.json['other_key'])

    result_dict = {"output": result}
    return result_dict


@app.route("/Translation", methods=['POST'])
def Translation():
    if not request.json or 'text' not in request.json:
        abort(400)

    text = request.json['text']

    result = translate_text(text, 'ar')

    result_dict = {"output": result}
    return result_dict


@app.route("/FaceRecognitionTraining", methods=['POST'])
def Training_Faces():
    if not request.json or 'urls' not in request.json:
        abort(400)

    all_urls = json.loads(request.json['urls'])

    known_names = []
    known_faces = []

    for key, value in all_urls.items():
        response = urllib.request.urlopen(value.replace('\\', ''))
        image = face_recognition.load_image_file(response)

        encoding = face_recognition.face_encodings(image)[0]

        known_faces.append(encoding)
        known_names.append(key)

    data = {}
    for key in known_names:
        for value in known_faces:
            data[key] = value.tolist()
            known_faces.remove(value)
            break

    new_data = json.dumps(data)
    result_dict = {"output": new_data}
    return result_dict


@app.route("/FaceRecognitionTesting", methods=['POST'])
def Recognize_Face():
    if not request.json or 'encodings' not in request.json:
        abort(400)

    if not request.json or 'image' not in request.json:
        abort(400)

    if request.json['encodings'] != "":
        all_face_encodings = json.loads(request.json['encodings'])
    else:
        result_dict = {"output": "Unknown Person"}
        return result_dict

    im_b64 = request.json['image']

    img_bytes = base64.b64decode(im_b64.encode('utf-8'))

    img = io.BytesIO(img_bytes)

    known_names = list(all_face_encodings.keys())
    known_faces = np.array(list(all_face_encodings.values()))

    image = face_recognition.load_image_file(img)

    locations = face_recognition.face_locations(image, model=MODEL)

    encodings = face_recognition.face_encodings(image, locations)

    for face_encoding, face_location in zip(encodings, locations):

        results = face_recognition.compare_faces(known_faces, face_encoding, TOLERANCE)
        match = None

        if True in results:
            match = known_names[results.index(True)]
            result = "" + match

        check = all(element == False for element in results)

        if check:
            result = "Unknown Person"

    result_dict = {"output": result}
    return result_dict


# app.run(debug=True)
