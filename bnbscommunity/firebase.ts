// Import the functions you need from the SDKs you need
import { initializeApp } from "firebase/app";
import { getFirestore } from 'firebase/firestore';
// TODO: Add SDKs for Firebase products that you want to use
// https://firebase.google.com/docs/web/setup#available-libraries

// Your web app's Firebase configuration
// For Firebase JS SDK v7.20.0 and later, measurementId is optional
const firebaseConfig = {
  apiKey: "AIzaSyDkUUq9vRdbiBQK5XzmLylPsVXP_gH3tnU",
  authDomain: "project-717dce1d-b530-431a-b19.firebaseapp.com",
  projectId: "project-717dce1d-b530-431a-b19",
  storageBucket: "project-717dce1d-b530-431a-b19.firebasestorage.app",
  messagingSenderId: "275599637949",
  appId: "1:275599637949:web:96ad0cbb841651d4d5a6d4",
  measurementId: "G-SS1C7ZTWSL"
};

// Initialize Firebase
const app = initializeApp(firebaseConfig);
const db = getFirestore(app, 'aitrading');

export { db };